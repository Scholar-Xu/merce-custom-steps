package com.inforefiner.custom.steps.parser;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.inforefiner.custom.util.ConvertUtil;
import com.merce.woven.common.FieldDesc;
import com.merce.woven.common.SchemaMiniDesc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.util.List;
import java.util.Map;

/**
 * Kafka发送的消息是json格式，并且一个消息中json是数据的行，例如：[{},{},{}]
 * 当前类的作用就是把json格式的消息转换成多个Row，每个Row对应于数组中的一个对象，即{}
 */
@Slf4j
public class JsonMsg2MultiRowFunction extends RichFlatMapFunction<Row, Row> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SchemaMiniDesc schema;

    private int arity;
    private Row row;

    //metrics
    private Counter inputCounter;
    private Counter outputCounter;
    private Counter mismatchCounter;
    private Counter errorCounter;

    public JsonMsg2MultiRowFunction(RowTypeInfo rowTypeInfo, SchemaMiniDesc schema) {
        this.arity = rowTypeInfo.getArity();
        this.row = new Row(this.arity);
        this.schema = schema;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        //register metrics
        MetricGroup metricGroup = getRuntimeContext().getMetricGroup().addGroup("parser_message");
        this.inputCounter = metricGroup.counter("input");
        this.outputCounter = metricGroup.counter("output");
        this.mismatchCounter = metricGroup.counter("mismatch");
        this.errorCounter = metricGroup.counter("error");
    }

    @Override
    public void flatMap(Row in, Collector<Row> out) throws Exception {
        try {
            inputCounter.inc();
            String originMsg = in.getField(0).toString();

            JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(List.class, Map.class);
            List<Map<String, Object>> msgList = OBJECT_MAPPER.readValue(originMsg, javaType);
            for (Map<String, Object> msg : msgList) {
                List<FieldDesc> fields = schema.getFields();
                int position = 0;
                for (FieldDesc field : fields) {
                    String fieldName = field.getName();
                    String type = field.getType();

                    Map<String, Object> msgMap = new JsonFlattener(OBJECT_MAPPER.writeValueAsString(msg)).flattenAsMap();

                    String filedValue = msgMap.getOrDefault(fieldName, "").toString();

                    if (StringUtils.isBlank(filedValue) || StringUtils.equalsIgnoreCase(filedValue, "null")) {
                        row.setField(position, ConvertUtil.castNull(type));
                    } else {
                        row.setField(position, ConvertUtil.convert(filedValue, type));
                    }

                    position++;
                }
                log.debug("applying raw data {}", row);
                out.collect(row);
                this.outputCounter.inc();
            }
        } catch (Exception e) {
            log.error("parser message {} throw exception", in, e);
            errorCounter.inc();
        }
    }

}
