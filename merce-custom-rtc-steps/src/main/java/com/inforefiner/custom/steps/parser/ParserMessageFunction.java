package com.inforefiner.custom.steps.parser;

import com.inforefiner.custom.util.ConvertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.types.Row;
import org.apache.flink.util.Collector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ParserMessageFunction extends RichFlatMapFunction<Row, Row> {

    private ParserMessageSettings settings;

    private RowTypeInfo outRowTypeInfo;

    private int arity;

    private Row row = null;

    private String splitRegex;

    private Map<String, List<Integer>> ignoreFieldMapping = new HashMap<>();

    //metrics
    private Counter inputCounter;

    private Counter outputCounter;

    private Counter errorCounter;

    public ParserMessageFunction(RowTypeInfo outRowTypeInfo, ParserMessageSettings settings) {
        this.arity = outRowTypeInfo.getArity();
        this.outRowTypeInfo = outRowTypeInfo;
        this.row = new Row(arity);
        this.settings = settings;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        try {
            String dataFlagSeparator = settings.getDataFlagSeparator();
            String flagColumnSeparator = settings.getDataFlagColumnSeparator();
            String flagKVSeparator = settings.getDataFlagKVSeparator();

            this.splitRegex = settings.getSplitRegex();

            /*
                忽略字段配置
            */
            String dataFlagIgnoreConfig = settings.getDataFlagIgnoreConfig();
            if (StringUtils.isNoneEmpty(dataFlagIgnoreConfig)) {
                String[] ignoreWithIndexes = dataFlagIgnoreConfig.split(flagColumnSeparator);
                for (String ignoreWithIndex : ignoreWithIndexes) {
                    String[] ignoreFlags = ignoreWithIndex.split(flagKVSeparator);
                    String ignoreFlagKey = ignoreFlags[0];
                    String ignoreFlagIndexesStr = ignoreFlags[1];
                    String[] ignoreFlagIndexes = ignoreFlagIndexesStr.split(dataFlagSeparator);
                    List<Integer> ignoreFlagIndexList = new ArrayList<>();
                    for (String ignoreFlagIndex : ignoreFlagIndexes) {
                        int index = Integer.parseInt(ignoreFlagIndex);
                        ignoreFlagIndexList.add(index);
                    }
                    this.ignoreFieldMapping.put(ignoreFlagKey, ignoreFlagIndexList);
                }
                log.info("ignoreFieldMapping {}", ignoreFieldMapping);
            }
        } catch (Exception e) {
            throw new RuntimeException("prepare process parser config throw exception", e);
        }

        //register metrics
        MetricGroup metricGroup = getRuntimeContext().getMetricGroup().addGroup("parser_message");
        this.inputCounter = metricGroup.counter("input");
        this.outputCounter = metricGroup.counter("output");
        this.errorCounter = metricGroup.counter("error");
    }

    public void flatMap(Row in, Collector<Row> out) throws Exception {
        try {
            inputCounter.inc();
            String message = in.getField(0).toString();
            String[] strValues = message.split(splitRegex, -1);

            /*
             * 1. 给定一个标志位和预期值，如果标志位的值与预期值不同，则忽略该条数据，否则跳到2
             * 2. 如果字段数与schema字段数不一致,跳到3，一致跳到4
             * 3. 对字段进行筛选，移除给定下标的字段，跳到4
             * 4. 对处理后的数据字段个数进行判断，如果与schema字段数不匹配，则丢弃
             * */
            String[] flagColumnValues = settings.getDataFlagColumnValues();
            int flagColumnIndex = settings.getDataFlagColumnIndex();
            String flagSeparator = settings.getDataFlagSeparator();
            if (flagColumnValues != null && ArrayUtils.isNotEmpty(flagColumnValues)) {
                String strValue = strValues[flagColumnIndex];
                /* 1. */
                if (!ArrayUtils.contains(flagColumnValues, strValue)) {
                    log.debug("Skip message {}, because the expected value of the specified fields do not match. " +
                                    "expected value is {}, value is {}", message,
                            StringUtils.join(flagColumnValues, flagSeparator),
                            strValue);
                    return;
                } else {
                    /* 2. */
                    if (strValues.length != arity && ignoreFieldMapping.containsKey(strValue)) {
                        List<Integer> indexes = ignoreFieldMapping.get(strValue);
                        for (Integer ignoreFieldIndex : indexes) {
                            strValues = ArrayUtils.remove(strValues, ignoreFieldIndex);
                        }
                    }
                }
            }

            /* 3. */
            if ((strValues.length != arity)) {
                log.debug("Csv data invalid: length is not enough! expect " + arity + " columns, but got "
                        + strValues.length);
                log.debug("Skip message {}, separator is {}, because the number of fields does not match.", message);
                log.debug("split data is {}", StringUtils.join(strValues, ";"));
                return;
            }

            log.debug("applying raw data {}", StringUtils.join(strValues, flagSeparator));
            for (int i = 0; i < arity; i++) {
                String type = outRowTypeInfo.getTypeAt(i).toString();
                if ("null".equalsIgnoreCase(strValues[i])) {
                    row.setField(i, ConvertUtil.castNull(type));
                } else {
                    row.setField(i, ConvertUtil.convert(strValues[i], type));
                }
            }

            if (row != null) {
                out.collect(row);
                this.outputCounter.inc();
            }
        } catch (Exception e) {
            log.error("parser message {} throw exception", in, e);
            errorCounter.inc();
        }
    }

}
