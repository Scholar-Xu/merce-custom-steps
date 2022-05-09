package com.inforefiner.custom.steps.sink.elasticsearch;

import com.github.wnameless.json.unflattener.JsonUnflattener;
import com.inforefiner.custom.util.FlinkJsonBuilder;
import com.merce.woven.common.FieldDesc;
import com.merce.woven.common.SchemaMiniDesc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.streaming.connectors.elasticsearch.ElasticsearchSinkFunction;
import org.apache.flink.streaming.connectors.elasticsearch.RequestIndexer;
import org.apache.flink.types.Row;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.xcontent.XContentType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1. 支持自定义elasticsearch index
 * 2. 支持json unflatten,输出带嵌套的json
 */
@Slf4j
public class ESSinkFunction implements ElasticsearchSinkFunction<Row> {

    private static final long serialVersionUID = -6085502286047452151L;

    private final RowTypeInfo rowTypeInfo;

    private final SchemaMiniDesc schema;

    private final String indexColumn;

    private final String jsonColumn;

    private final String indexType;

    private transient Map<String, FieldDesc> fieldMapping;

    //metrics
    private transient Counter inputCounter;

    private boolean init = true;

    private FlinkJsonBuilder jsonBuilder;

    public ESSinkFunction(RowTypeInfo rowTypeInfo, SchemaMiniDesc schema,
                          String indexColumn, String jsonColumn, String indexType) {
        this.rowTypeInfo = rowTypeInfo;
        this.schema = schema;
        this.indexColumn = indexColumn;
        this.jsonColumn = jsonColumn;
        this.indexType = indexType;
    }

    @Override
    public void process(Row element, RuntimeContext ctx, RequestIndexer indexer) {
        if (init && inputCounter == null) {

            fieldMapping = new HashMap<>();
            List<FieldDesc> fields = schema.getFields();
            for (FieldDesc field : fields) {
                String alias = field.getAlias();
                String name = field.getName();
                String key = StringUtils.isNoneEmpty(alias) ? alias : name;
                fieldMapping.put(key, field);
            }

            this.jsonBuilder = FlinkJsonBuilder.getInstance();

            //register metrics
            MetricGroup metricGroup = ctx.getMetricGroup().addGroup("custom_group");
            this.inputCounter = metricGroup.counter("input");
            init = false;
        } else {
            this.inputCounter.inc();
        }

        indexer.add(createIndexRequest(element));
    }

    private IndexRequest createIndexRequest(Row row) {
        Map<String, Object> json = new HashMap<>();
        String index;
        int indexColumnIdx = rowTypeInfo.getFieldIndex(indexColumn);
        if (indexColumnIdx != -1) {
            Object indexObj = row.getField(indexColumnIdx);
            if (indexObj != null && StringUtils.isNoneEmpty(indexObj.toString())) {
                index = indexObj.toString();
            } else {
                throw new RuntimeException("ElasticSearch index is empty. index column is " + indexColumn);
            }
        } else {
            index = indexColumn;
        }

        for (String fieldName : fieldMapping.keySet()) {
            if (indexColumn.equals(fieldName)) { //不输出index列
                continue;
            }
            FieldDesc fieldDesc = fieldMapping.get(fieldName);
            String name = fieldDesc.getName();
            String key = StringUtils.isNoneEmpty(name) ? name : fieldName;
            int fieldIndex = rowTypeInfo.getFieldIndex(key);
            Object fieldValue = row.getField(fieldIndex);
            if (StringUtils.isNotBlank(jsonColumn)
                    && key.equals(jsonColumn)
                    && fieldValue != null) {
                /*
                * 输出json格式的数据并且不带有schema的field名称
                * */
                Map<String, Object> map = jsonBuilder.fromJson(fieldValue.toString(), Map.class);
                json.putAll(map);
            } else if (fieldValue != null) {
                /*
                 * 输出json格式的数据并且带有schema的field名称
                 * */
                String type = rowTypeInfo.getTypeAt(key).toString();
                Object convertedV = convert(fieldValue, type);
                json.put(fieldName, convertedV);
            } else {
                json.put(fieldName, null);
            }
        }

        String toJson = jsonBuilder.toJson(json);
        String unflattenJson = JsonUnflattener.unflatten(toJson);

        IndexRequest indexRequest = Requests.indexRequest()
                .index(index)
                .type(indexType)
                .source(unflattenJson, XContentType.JSON);
        log.debug("Input row {}, indexRequest {}", row, indexRequest);
        return indexRequest;
    }

    private Object convert(Object value, String type) {
        log.debug("Convert data[{}] to type[{}]", value, type);
        if (type.contains("(")) {
            type = type.substring(0, type.indexOf("("));
        }
        switch (type.toLowerCase()) {
            case "byte":
            case "short":
            case "int":
            case "integer":
            case "bigint":
            case "long":
            case "float":
            case "double":
            case "string":
            case "boolean":
                return value;
            case "byte[]":
            case "binary":
                byte[] bytes = (byte[]) value;
                return Base64.getEncoder().encodeToString(bytes);
            case "date":
                Date date = (Date) value;
                return date.getTime();
            case "timestamp":
                Timestamp timestamp = (Timestamp) value;
                return timestamp.getTime();
            case "decimal":
            case "bigdecimal":
                BigDecimal bigDecimal = (BigDecimal) value;
                return bigDecimal.doubleValue();
            default:
                throw new RuntimeException("Not support type " + type);
        }
    }

}

