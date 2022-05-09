package com.inforefiner.custom.steps.filter;

import com.inforefiner.custom.Version;
import com.inforefiner.custom.util.ConvertUtil;
import com.merce.woven.annotation.Setting;
import com.merce.woven.annotation.StepBind;
import com.merce.woven.flow.spark.flow.Step;
import com.merce.woven.flow.spark.flow.StepSettings;
import com.merce.woven.flow.spark.flow.StepValidateResult;
import com.merce.woven.step.StepCategory;
import lombok.Getter;
import lombok.Setter;
import org.apache.flink.api.common.functions.RichFilterFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.types.Row;

@StepBind(id = "rtc_filter_" + Version.version, settingClass = FilterSettings.class)
public class FilterStep extends Step<FilterSettings, DataStream<Row>> {

    private static final long serialVersionUID = 3056657271266356981L;
    private int keyIndex;

    private Object keyValue;

    /**
     * 构造函数
     */
    public FilterStep() {
        this.stepCategory = StepCategory.TRANSFORM;
    }

    /**
     * 返回配置类
     * @return
     */
    @Override
    public FilterSettings initSettings() {
        return new FilterSettings();
    }

    /**
     * 预处理
     */
    @Override
    public void setup() {
        DataStream<Row> input = this.input();
        RowTypeInfo rowTypeInfo = (RowTypeInfo) input.getType();
        this.keyIndex = settings.keyIndex;
        String keyValue = settings.keyValue;
        String type = rowTypeInfo.getTypeAt(keyIndex).toString();
        Object castValue = ConvertUtil.cast(keyValue, type);
        this.keyValue = castValue;
    }

    /**
     * 数据处理
     */
    @Override
    public void process() {
        logger.info("process step:" + this.getId());
        //1. 获取该Step的输入
        DataStream<Row> input = this.input();

        //2. 对数据进行自定义的处理
        SingleOutputStreamOperator<Row> filteredStream = input.filter(new RichFilterFunction<Row>() {
            @Override
            public boolean filter(Row value) throws Exception {
                Object field = value.getField(keyIndex);
                return field.equals(keyValue);
            }
        });

        //3. 将处理结果添加到输出，以便下游Step获取数据
        this.addOutput(filteredStream);
    }

    /**
     * 校验，前端点击Step确定的时候会调用此函数
     * @return
     */
    @Override
    public StepValidateResult validate() {
        StepValidateResult validationResult = new StepValidateResult(this.id);
        if (settings.getKeyIndex() == -1) {
            validationResult.addError("condition", "condition setting is empty");
        }
        return validationResult;
    }

}

/**
 * Step参数类
 * 支持int,long,String,boolean类型参数的UI自动化展示
 */
@Getter
@Setter
class FilterSettings extends StepSettings {

    @Setting(description = "key")
    int keyIndex = -1;

    @Setting(description = "key value")
    String keyValue;

}
