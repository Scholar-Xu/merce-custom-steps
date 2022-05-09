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
import org.apache.commons.lang.StringUtils;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.types.Row;

import java.util.ArrayList;
import java.util.List;

@StepBind(id = "rtc_multi_msg_json_parser_" + Version.version, settingClass = JsonMsg2MultiRowSettings.class)
public class JsonMsg2MultiRowStep  extends Step<JsonMsg2MultiRowSettings, DataStream<Row>> {

    private static final long serialVersionUID = -2734821584117293731L;
    private SchemaMiniDesc schema;
    private RowTypeInfo rowTypeInfo;
    private JsonMsg2MultiRowFunction jsonMsg2MultiRowFunction;

    public JsonMsg2MultiRowStep() {
        this.stepCategory = StepCategory.TRANSFORM;
    }

    @Override
    public JsonMsg2MultiRowSettings initSettings() {
        return new JsonMsg2MultiRowSettings();
    }

    @Override
    public void setup() {
        this.schema = settings.getSchema();
        ArrayList<FieldDesc> outputFields = new ArrayList<>();
        List<FieldDesc> fields = schema.getFields();
        for (FieldDesc field : fields) {
            if (StringUtils.isNotBlank(field.getAlias())) {
                outputFields.add(new FieldDesc(field.getAlias(), field.getType(), null, field.getDescription()));
            } else {
                outputFields.add(new FieldDesc(field.getName(), field.getType(), null, field.getDescription()));
            }
        }
        this.rowTypeInfo = ConvertUtil.buildRowTypeInfo(outputFields);
        this.jsonMsg2MultiRowFunction = new JsonMsg2MultiRowFunction(rowTypeInfo, schema);
    }

    @Override
    public void process() {
        DataStream<Row> input = this.input();
        SingleOutputStreamOperator<Row> result = input
                .flatMap(jsonMsg2MultiRowFunction)
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

    @Override
    public DataStream<Row> output(String dName) {
        return super.output(dName);
    }
}

@Setter
@Getter
class JsonMsg2MultiRowSettings extends StepSettings {

    @Setting(description = "选择输出字段schema", selectType = SelectType.SCHEMA)
    private String schemaName;

    @Setting(defaultValue = "0", required = false, advanced = true, description = "Step并行度，如果是0则使用Job的并行度")
    private int parallelism = 0;

    private SchemaMiniDesc schema;

}
