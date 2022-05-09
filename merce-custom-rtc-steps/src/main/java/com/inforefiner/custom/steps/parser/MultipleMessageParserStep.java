package com.inforefiner.custom.steps.parser;

import com.inforefiner.custom.Version;
import com.inforefiner.custom.util.ConvertUtil;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import com.merce.woven.annotation.StepBind;
import com.merce.woven.common.FieldDesc;
import com.merce.woven.common.SchemaMiniDesc;
import com.merce.woven.common.StepFieldGroup;
import com.merce.woven.flow.spark.flow.Step;
import com.merce.woven.flow.spark.flow.StepSettings;
import com.merce.woven.step.StepCategory;
import lombok.Getter;
import lombok.Setter;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.types.Row;

import java.util.List;

@StepBind(id = "rtc_multiple_message_parser_" + Version.version, settingClass = MultipleMessageParserSettings.class)
public class MultipleMessageParserStep extends Step<MultipleMessageParserSettings, DataStream<Row>> {

    private static final long serialVersionUID = -3120651999060047162L;
    private SchemaMiniDesc schema;

    private RowTypeInfo rowTypeInfo;

    private RichFlatMapFunction<Row, Row> parserMessageFunction;

    public MultipleMessageParserStep() {
        this.stepCategory = StepCategory.TRANSFORM;
    }

    @Override
    public MultipleMessageParserSettings initSettings() {
        return new MultipleMessageParserSettings();
    }

    @Override
    public void setup() {
        this.schema = settings.getSchema();
        this.rowTypeInfo = ConvertUtil.buildRowTypeInfo(schema.getFields());
        this.parserMessageFunction = new MultiMsgParserHexFunction(rowTypeInfo, settings);
    }

    @Override
    public void process() {
        DataStream<Row> input = this.input();
        SingleOutputStreamOperator<Row> result = input
                .flatMap(parserMessageFunction)
                .returns(rowTypeInfo)
                .setParallelism(settings.getOrElse("parallelism", 0))
                .uid(this.id);
        this.addOutput(result);
    }

    @Override
    public StepFieldGroup fields() {
        SchemaMiniDesc schema = settings.getSchema();
        List<FieldDesc> fields = schema.getFields();
        return this.addOutputFields(fields);
    }
}

@Getter
@Setter
class MultipleMessageParserSettings extends StepSettings {

    @Setting(description = "选择输出字段schema", selectType = SelectType.SCHEMA)
    private String schemaName;

    /*
     * 字段分隔符
     * 1. 当填写内容为空时，不进行分割，按照整行输出
     * 2. 当填写内容非空时，按照Separator将数据切割为多个字段内容
     * */
    @Setting(defaultValue = ",", description = "列分隔符", required = false)
    private String separator = ",";

    @Setting(defaultValue = "0", required = false, advanced = true, description = "Step并行度，如果是0则使用Job的并行度")
    private int parallelism = 0;

    /*
     * 消息头，默认为空。可选择是否填写，填写时支持多个值，多个值之间使用分隔符分割
     * 1. 填写内容非空时，仅保留处理的原始消息中通过headerSeparator切割后消息头内容与配置的消息头内容一致的原始消息的消息头和消息体
     * 2. 填写为空时，不过滤数据
     * */
    @Setting(required = false, advanced = true, description = "消息头。多个值按逗号分隔: header1,header2,header3")
    private String headers;

    /*
     * headerSeparator：head与message的分隔符
     * 默认为空，支持是否填写内容，填写内容支持十六进制和非十六进制（提供是否为十六进制的开关选项）
     * 1. 填写内容非空时，可使用headerSeparator将原始消息切割为消息头和消息体。
     * 2. 填写内容为空时，不切割原始消息
     * */
    @Setting(required = false, advanced = true, description = "消息头分隔符")
    private String headerSeparator;

    /*
     * 消息头分隔符是否十六进制的标记，值选项：true、false，默认值为false
     * 1. 当标记为true时，headerSeparator配置项按照十六进制填写以及解析
     * */
    @Setting(required = false, advanced = true, defaultValue = "false", values = {"true", "false"},
            description = "消息头分割符为十六进制字符")
    private Boolean headerHex;

    /*
     * 消息体中字段分隔符、多行分隔符是否十六进制的标记，值选项：true、false，默认值为false
     * 1. 当标记为true时，messageSeparator、Separator配置项都按照十六进制填写以及解析
     * */
    @Setting(required = false, advanced = true, defaultValue = "false", values = {"true", "false"},
            description = "消息分隔符和列分割符为十六进制字符")
    private Boolean hex;

    /*
     * 多行消息体之间分隔符，支持十六进制，支持是否填写内容
     * 1. 当填写内容为空时，按照整行消息处理，不进行多行消息分割
     * 2. 当填写内容非空时，按照多行消息处理，将消息体按照messageSeparator配置分隔符进行切割为多行消息
     * */
    @Setting(required = false, advanced = true, defaultValue = "",
            description = "消息分隔符")
    private String messageSeparator = "";

    private SchemaMiniDesc schema;

}
