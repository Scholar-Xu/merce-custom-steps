package com.inforefiner.custom.steps.sink.jdbc;

import com.inforefiner.custom.Version;
import com.inforefiner.custom.util.AesCryptor;
import com.inforefiner.custom.util.CheckUtil;
import com.inforefiner.custom.util.ConvertUtil;
import com.inforefiner.custom.util.StepFieldUtil;
import com.merce.woven.annotation.Select;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import com.merce.woven.annotation.StepBind;
import com.merce.woven.common.DatasetMiniDesc;
import com.merce.woven.common.FieldDesc;
import com.merce.woven.common.SchemaMiniDesc;
import com.merce.woven.flow.spark.flow.Step;
import com.merce.woven.flow.spark.flow.StepSettings;
import com.merce.woven.flow.spark.flow.StepValidateResult;
import com.merce.woven.step.StepCategory;
import lombok.Getter;
import lombok.Setter;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.types.Row;

import java.util.List;
import java.util.Optional;

@StepBind(id = "custom_sink_jdbc_upsert_" + Version.version, outputCount = 0, outputIds = {},
        settingClass = JDBCSinkSettings.class)
public class JDBCSinkStep extends Step<JDBCSinkSettings, DataStream<Row>> {

    private static final long serialVersionUID = -9167541816390921997L;
    private String table;

    private String username;

    private String password;

    private String url;

    private String driverClassName;

    private int batchInterval;

    public JDBCSinkStep() {
        this.stepCategory = StepCategory.SINK;
    }

    @Override
    public JDBCSinkSettings initSettings() {
        return new JDBCSinkSettings();
    }

    @Override
    public StepValidateResult validate() {
        StepValidateResult validationResult = new StepValidateResult(this.id);
        CheckUtil.checkingString("username", settings.getUsername(), validationResult);
        CheckUtil.checkingString("url", settings.getUrl(), validationResult);
        CheckUtil.checkingString("driverClassName", settings.getDriver(), validationResult);

        int batchSize = settings.getBatchSize();
        if (batchSize <= 0) {
            validationResult.addError("batchSize", "batchSize must > 0");
        }

        return validationResult;
    }

    @Override
    public void setup() {
        this.table = settings.getTable();
        this.username = settings.getUsername();
        this.password = settings.getPassword();
        this.driverClassName = settings.getDriver();
        this.url = settings.getUrl();
        this.batchInterval = settings.getBatchSize();
        logger.info("JDBC configuration: username is {}", username);
        logger.info("JDBC configuration: driverClassName is {}", driverClassName);
        logger.info("JDBC configuration: url is {}", url);
        logger.info("JDBC configuration: batchInterval is {}", batchInterval);
    }

    @Override
    public void process() {
        DataStream<Row> inputStream = this.input();
        RowTypeInfo rowTypeInfo = (RowTypeInfo) inputStream.getType();
        SchemaMiniDesc schema = settings.getSchema();
        logger.info("Step[{}] input row type info {}", this.id, rowTypeInfo);

        List<FieldDesc> inputFields = this.inputFields();
        String[] fields = StepFieldUtil.getFieldAliasesArray(inputFields);

        String actionColumn = settings.getActionColumn();
        String[] primaryKeyNames = settings.getPrimaryKeyNames();
        String[] primaryKeyColumns = settings.getPrimaryKeyColumns();
        int actionIndex = rowTypeInfo.getFieldIndex(actionColumn);
        int[] primaryKeyIdx = new int[primaryKeyColumns.length];
        for (int i = 0; i < primaryKeyColumns.length; i++) {
            String primaryKeyColumn = primaryKeyColumns[i];
            primaryKeyIdx[i] = rowTypeInfo.getFieldIndex(primaryKeyColumn);
        }

        String[] schemaFields = schema.fetchAllFieldsNames();
        Optional<JDBCDialect> jdbcDialectOptional = JDBCDialects.get(this.url);
        if (!jdbcDialectOptional.isPresent()) {
            throw new RuntimeException("UnSupport JDBC for url " + this.url);
        }
        JDBCDialect jdbcDialect = jdbcDialectOptional.get();
        String query = jdbcDialect.buildQuery(table, schemaFields);
        String update = jdbcDialect.buildUpdate(table);
        String delete = jdbcDialect.buildDelete(table);
        String insert = jdbcDialect.buildInsert(table, schemaFields);
        String[] types = StepFieldUtil.getFieldTypeArray(inputFields);
        int[] sqlTypes = new int[fields.length];
        for (int i = 0; i < sqlTypes.length; i++) {
            sqlTypes[i] = ConvertUtil.toSqlType(types[i]);
        }

        String passwordDecrypt = new AesCryptor().decrypt(password);

        JDBCOutputFormat jdbcOutputFormat = JDBCOutputFormat.buildJDBCOutputFormat()
                .setUsername(username)
                .setPassword(passwordDecrypt)
                .setDBUrl(url)
                .setQuery(query)
                .setUpdate(update)
                .setDelete(delete)
                .setInsert(insert)
                .setRowTypeInfo(rowTypeInfo)
                .setSchema(schema)
                .setActionColumnIdx(actionIndex)
                .setPrimaryKeyNames(primaryKeyNames)
                .setPrimaryKeyIdx(primaryKeyIdx)
                .setDrivername(driverClassName)
                .setBatchInterval(batchInterval)
                .setSqlTypes(sqlTypes)
                .finish();

        inputStream
                .addSink(new JDBCSinkFunction(jdbcOutputFormat))
                .setParallelism(settings.getOrElse("parallelism", 0))
                .name(this.id)
                .uid(this.id);
    }
}

@Getter
@Setter
class JDBCSinkSettings extends StepSettings {

    /**
     * 选择的数据集名称
     */
    @Setting(selectType = SelectType.DATASET, description = "数据集")
    private String datasetName;

    @Setting(defaultValue = "JDBC", values = {"JDBC"}, description = "Sink输出类型", compositeStep = true)
    private String type;

    private DatasetMiniDesc dataset;

    private SchemaMiniDesc schema;

    @Setting(defaultValue = "0", required = false, advanced = true, description = "Step并行度，如果是0则使用Job的并行度")
    private int parallelism = 0;

    @Setting(description = "表名")
    private String table;

    @Setting(description = "用户名")
    private String username;

    @Setting(description = "密码")
    private String password;

    @Setting(description = "url")
    private String url;

    @Setting(description = "Driver Class Name")
    private String driver;

    @Setting(description = "数据库操作标识字段", select = Select.INPUT, selectType = SelectType.FIELDS)
    private String actionColumn;

    @Setting(description = "主键字段名称(需要与主键字段值保持一样的顺序)")
    private String[] primaryKeyNames;

    @Setting(description = "主键字段值(需要与主键字段名称保持一样的顺序)", select = Select.INPUT, selectType = SelectType.FIELDS)
    private String[] primaryKeyColumns;

    @Setting(description = "批处理阈值", defaultValue = "500", advanced = true, required = false)
    private int batchSize = 500;

}
