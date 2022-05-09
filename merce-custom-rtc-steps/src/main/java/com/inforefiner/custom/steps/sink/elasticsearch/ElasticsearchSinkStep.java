package com.inforefiner.custom.steps.sink.elasticsearch;

import com.inforefiner.custom.Version;
import com.inforefiner.custom.util.CheckUtil;
import com.merce.woven.annotation.Select;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import com.merce.woven.annotation.StepBind;
import com.merce.woven.common.DatasetMiniDesc;
import com.merce.woven.common.SchemaMiniDesc;
import com.merce.woven.flow.spark.flow.Step;
import com.merce.woven.flow.spark.flow.StepSettings;
import com.merce.woven.flow.spark.flow.StepValidateResult;
import com.merce.woven.step.StepCategory;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.connectors.elasticsearch.ActionRequestFailureHandler;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkBase;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.streaming.connectors.elasticsearch7.ElasticsearchSink;
import org.apache.flink.types.Row;
import org.apache.flink.util.ExceptionUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.nio.reactor.IOReactorExceptionHandler;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.common.util.concurrent.EsRejectedExecutionException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 自定义Json ES Sink Step
 * 1. index可配置成参数
 * 2. 输出json格式时支持嵌套json
 */
@StepBind(id = "es_json_sink_" + Version.version, outputCount = 0, outputIds = {},
        settingClass = ElasticsearchSinkSettings.class)
public class ElasticsearchSinkStep extends Step<ElasticsearchSinkSettings, DataStream<Row>> {

    private static final long serialVersionUID = 8593069907543014440L;

    private DatasetMiniDesc dataset;

    private SchemaMiniDesc schema;

    private String clusterName;

    private List<HttpHost> httpHosts = new ArrayList<>();

    private String indexColumn;

    private String jsonColumn;//string类型数据直接按照json写入ES,跳过schema校验

    private String indexType;

    private String httpAuthUser;

    private String httpAuthPassword;

    private int numMaxActions;
    private int maxSizeMb;
    private int flushInterval;

    private boolean flushBackoffEnable;
    private ElasticsearchSinkBase.FlushBackoffType flushBackoffType;
    private long delayMillis;
    private int maxRetries;
    private boolean ignoreErrors = false;
    
    private int connectTimeout = 5000;
    private int socketTimeout = 30000;

    public ElasticsearchSinkStep() {
        this.stepCategory = StepCategory.SINK;
    }

    @Override
    public ElasticsearchSinkSettings initSettings() {
        return new ElasticsearchSinkSettings();
    }

    @Override
    public void setup() {
        this.dataset = settings.getDataset();
        this.schema = settings.getSchema();
        this.clusterName = settings.getClusterName();
        String httpScheme = settings.getHttpScheme();
        String ipAddressesStr = settings.getIpAddresses();
        String[] ipAddresses = ipAddressesStr.split(";");
        for (String ipAddress : ipAddresses) {
            String[] ipAndPort = ipAddress.split(":");
            String ip = ipAndPort[0];
            int port = Integer.parseInt(ipAndPort[1]);
            HttpHost httpHost = new HttpHost(ip, port, httpScheme);
            httpHosts.add(httpHost);
        }
        String indexColumn = settings.getIndexColumn();
        this.indexColumn = StringUtils.isNoneEmpty(indexColumn) ? //
                indexColumn : //
                dataset.getStorageConfiguration().get("index").toString(); //
        this.jsonColumn = settings.getJsonColumn();
        this.indexType = settings.getIndexType();
        this.httpAuthUser = settings.getHttpAuthUser();
        this.httpAuthPassword = settings.getHttpAuthPassword();
        this.numMaxActions = settings.getBatchRows();
        this.maxSizeMb = settings.getBatchSize();
        this.flushInterval = settings.getFlushInterval();
        this.flushBackoffEnable = settings.isFlushBackoffEnable();
        this.flushBackoffType = ElasticsearchSinkBase.FlushBackoffType.valueOf(settings.getFlushBackoffType());
        this.delayMillis = settings.getDelayMillis();
        this.maxRetries = settings.getMaxRetries();
        this.ignoreErrors = settings.isIgnoreErrors();
        
        this.connectTimeout = settings.getConnectTimeout();
        this.socketTimeout = settings.getSocketTimeout();
    }

    @Override
    public void process() {
        DataStream<Row> input = this.input();
        RowTypeInfo rowTypeInfo = (RowTypeInfo) input.getType();

        // use a ElasticsearchSink.Builder to create an ElasticsearchSink
        ElasticsearchSink.Builder<Row> esSinkBuilder = new ElasticsearchSink.Builder<>(
                httpHosts,
                new ESSinkFunction(rowTypeInfo, schema, indexColumn, jsonColumn, indexType));

        // configuration for the bulk requests; this instructs the sink to emit after every element, otherwise they would be buffered
        esSinkBuilder.setBulkFlushMaxActions(numMaxActions);
        esSinkBuilder.setBulkFlushMaxSizeMb(maxSizeMb);
        if (flushInterval >= 0) {
            esSinkBuilder.setBulkFlushInterval(TimeUnit.SECONDS.toMillis(flushInterval));
        }

        // provide a RestClientFactory for custom configuration on the internally created REST client
        esSinkBuilder.setRestClientFactory(
                restClientBuilder -> {
                    restClientBuilder.setHttpClientConfigCallback(new RestClientBuilder.HttpClientConfigCallback() {
                        @Override
                        public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
                            // elasticsearch username and password
                            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                            credentialsProvider.setCredentials(AuthScope.ANY,
                                    new UsernamePasswordCredentials(httpAuthUser, httpAuthPassword));
                            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);

                            try {
                                DefaultConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
                                ioReactor.setExceptionHandler(new IOReactorExceptionHandler() {
                                    @Override
                                    public boolean handle(IOException e) {
                                        logger.warn("System may be unstable: IOReactor encountered a checked exception : "
                                                + e.getMessage(), e);
                                        return true; // Return true to note this exception as handled, it will not be re-thrown
                                    }

                                    @Override
                                    public boolean handle(RuntimeException e) {
                                        logger.warn("System may be unstable: IOReactor encountered a runtime exception : "
                                                + e.getMessage(), e);
                                        return true; // Return true to note this exception as handled, it will not be re-thrown
                                    }
                                });
                                httpClientBuilder.setConnectionManager(new PoolingNHttpClientConnectionManager(ioReactor));
                            } catch (IOReactorException e) {
                                throw new RuntimeException(e);
                            }

                            return httpClientBuilder;
                        }
                    });
                    restClientBuilder.setRequestConfigCallback(
                            requestConfigBuilder ->
                                    RequestConfig.custom()
                                            .setConnectTimeout(connectTimeout)
                                            .setSocketTimeout(socketTimeout));
                }
        );

        /*
         * 异常处理
         * */
        esSinkBuilder.setFailureHandler(new ActionRequestFailureHandler() {
            @Override
            public void onFailure(ActionRequest action, Throwable failure, int i, RequestIndexer indexer) throws Throwable {
                if (ExceptionUtils.findThrowable(failure, EsRejectedExecutionException.class).isPresent()) {
                    // full queue; re-add document for indexing
                    indexer.add(action);
                } else if (ExceptionUtils.findThrowable(failure, ElasticsearchParseException.class).isPresent()) {
                    // malformed document; simply drop request without failing sink
                    logger.warn("Drop request action {}, malformed document.", action);
                } else if (ExceptionUtils.findThrowable(failure, ElasticsearchException.class).isPresent()) {
                    if (!ignoreErrors) {
                        logger.error("Throw an elasticsearch exception because the setting does not ignore errors, action is {}", action, failure);
                        throw failure;
                    } else {
                        logger.error("Can't Parse action: {}", action, failure);
                    }
                } else {
                    // for all other failures, fail the sink
                    // here the failure is simply rethrown, but users can also choose to throw custom exceptions
                    throw failure;
                }
            }
        });

        esSinkBuilder.setBulkFlushBackoff(flushBackoffEnable);
        esSinkBuilder.setBulkFlushBackoffType(flushBackoffType);
        esSinkBuilder.setBulkFlushBackoffDelay(delayMillis);
        esSinkBuilder.setBulkFlushBackoffRetries(maxRetries);

        input.addSink(esSinkBuilder.build())
                .setParallelism(settings.getOrElse("parallelism", 0))
                .name(this.id)
                .uid(this.id);

    }

    @Override
    public StepValidateResult validate() {
        StepValidateResult validateResult = new StepValidateResult();
        CheckUtil.checkingString("clusterName", settings.getClusterName(), validateResult);
        CheckUtil.checkingString("ipAddresses", settings.getIpAddresses(), validateResult);
        String ipAddresses = settings.getIpAddresses();
        if (!ipAddresses.contains(":")) {
            validateResult.addError("ipAddresses", "ipAddresses %s is wrong.", ipAddresses);
        }
        CheckUtil.checkingString("indexColumn", settings.getIndexColumn(), validateResult);
        CheckUtil.checkingString("indexType", settings.getIndexType(), validateResult);
        CheckUtil.checkingString("httpAuthUser", settings.getHttpAuthUser(), validateResult);
        CheckUtil.checkingString("httpAuthPassword", settings.getHttpAuthPassword(), validateResult);
        return validateResult;
    }
}

@Getter
@Setter
class ElasticsearchSinkSettings extends StepSettings {

    /**
     * 选择的数据集名称
     */
    @Setting(selectType = SelectType.DATASET, description = "数据集")
    private String datasetName;

    @Setting(defaultValue = "ElasticSearch", values = {"ElasticSearch"}, description = "Source输入类型", compositeStep = true)
    private String type;

    private DatasetMiniDesc dataset;

    private SchemaMiniDesc schema;

    @Setting(description = "集群名称", defaultValue = "elasticsearch")
    private String clusterName;

    @Setting(description = "elasticsearch网络地址，ip1:port1;ip2:port2")
    private String ipAddresses;

    @Setting(description = "http schema", defaultValue = "http", values = {"http", "https"})
    private String httpScheme;

    @Setting(defaultValue = "0", required = false, advanced = true, description = "Step并行度，如果是0则使用Job的并行度")
    private int parallelism = 0;

    @Setting(description = "索引列(为空时使用数据集设置的索引)", selectType = SelectType.FIELDS, select = Select.INPUT,
            required = false, advanced = true)
    private String indexColumn;

    @Setting(description = "string类型数据直接按照json写入ES,跳过schema校验", selectType = SelectType.FIELDS, select = Select.INPUT,
            required = false, advanced = true)
    private String jsonColumn;

    @Setting(description = "type", required = false)
    private String indexType;

    @Setting(description = "用户名称", required = false)
    private String httpAuthUser;

    @Setting(description = "用户密码", required = false)
    private String httpAuthPassword;

    @Setting(description = "最大批处理缓存数据量", defaultValue = "1000", required = false, advanced = true)
    private int batchRows = 1000;

    @Setting(description = "最大批处理缓存数据大小,默认5M", defaultValue = "5", required = false, advanced = true)
    private int batchSize = 5;

    @Setting(description = "最大批处理缓存时间(秒),-1代表不设置,如果设置值需>=0",
            defaultValue = "-1", required = false, advanced = true)
    private int flushInterval = -1;

    @Setting(description = "延迟重试策略: 默认启用指数级间隔重试策略,初始等待50ms,8次重试",
            defaultValue = "true", required = false, advanced = true)
    private boolean flushBackoffEnable = true;

    @Setting(description = "CONSTANT(固定间隔)或EXPONENTIAL(指数级间隔)",
            defaultValue = "EXPONENTIAL", values = {"EXPONENTIAL", "CONSTANT"},
            scope = "flushBackoffEnable", scopeType = "boolean", bind = "true",
            required = false, advanced = true)
    private String flushBackoffType = "EXPONENTIAL";

    @Setting(description = "延迟重试间隔,对于CONSTANT类型,此值为每次重试间的间隔;对于EXPONENTIAL,此值为初始延迟",
            defaultValue = "50", scope = "flushBackoffEnable", scopeType = "boolean", bind = "true",
            required = false, advanced = true)
    private long delayMillis = 50;

    @Setting(description = "延迟重试次数",
            defaultValue = "8", scope = "flushBackoffEnable", scopeType = "boolean", bind = "true",
            required = false, advanced = true)
    private int maxRetries = 8;

    @Setting(description = "是否忽略ES Sink错误", defaultValue = "false", required = false, advanced = true)
    private boolean ignoreErrors = false;
    
    @Setting(description = "连接超时", defaultValue = "5000", required = false, advanced = true)
    private int connectTimeout = 5000;
    
    @Setting(description = "socket超时", defaultValue = "30000", required = false, advanced = true)
    private int socketTimeout = 30000;

}
