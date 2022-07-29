package com.inforefiner.custom.steps.parser;

import com.inforefiner.custom.util.ConvertUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

import java.io.Serializable;
import java.util.Arrays;

/*
 * 逻辑图: http://192.168.1.175:8090/download/attachments/23986366/%E5%9B%BE%E7%89%871.png?version=1&modificationDate=1638549166634&api=v2
 */
@Slf4j
public class MultiMsgParserHexFunction extends RichFlatMapFunction<Row, Row> {

    private RowTypeInfo outRowTypeInfo;
    private int arity;
    private Row row;

    private String[] headers;
    private String headerSeparator;
    private String messageSeparator;
    private String separator;

    private boolean ignoreCheckFieldSize;

    //metrics
    private Counter inputCounter;
    private Counter outputCounter;
    private Counter headerMismatchCounter;
    private Counter colMismatchCounter;
    private Counter errorCounter;

    public MultiMsgParserHexFunction(RowTypeInfo outRowTypeInfo, MultipleMessageParserSettings settings) {
        this.arity = outRowTypeInfo.getArity();
        this.outRowTypeInfo = outRowTypeInfo;
        this.row = new Row(arity);

        try {
            log.info("print message parser configuration:");
            String headers = settings.getHeaders();
            String headerSeparator = settings.getHeaderSeparator();
            Boolean headerHex = settings.getHeaderHex();
            String messageSeparator = settings.getMessageSeparator();
            String separator = settings.getSeparator();
            Boolean hex = settings.getHex();
            ignoreCheckFieldSize = settings.getIgnoreCheckFieldSize();
            log.info("-------- origin configuration --------");
            log.info("headers: {}", headers);
            log.info("headerSeparator: {}", headerSeparator);
            log.info("headerHex: {}", headerHex);
            log.info("messageSeparator: {}", messageSeparator);
            log.info("separator: {}", separator);
            log.info("hex: {}", hex);
            log.info("ignoreCheckFieldSize: {}", ignoreCheckFieldSize);

            boolean headerNotBlank = StringUtils.isNotEmpty(headers);
            this.headers = headerNotBlank ? StringUtils.split(headers, ',') : new String[0];
            this.headerSeparator = headerHex ? hexToStr(headerSeparator) : headerSeparator;
            this.messageSeparator = hex ? hexToStr(messageSeparator) : messageSeparator;
            this.separator = hex ? hexToStr(separator) : separator;
            log.info("-------- after hexToStr configuration --------");
            log.info("headers: {}", StringUtils.join(this.headers, ','));
            log.info("headerSeparator: {}", this.headerSeparator);
            log.info("messageSeparator: {}", this.messageSeparator);
            log.info("separator: {}", this.separator);
            log.info("schema: {}", outRowTypeInfo);
        } catch (Exception e) {
            throw new RuntimeException("prepare process parser config throw exception", e);
        }
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        //register metrics
        MetricGroup metricGroup = getRuntimeContext().getMetricGroup().addGroup("parser_message");
        this.inputCounter = metricGroup.counter("input");
        this.outputCounter = metricGroup.counter("output");
        this.headerMismatchCounter = metricGroup.counter("headerMismatch");
        this.colMismatchCounter = metricGroup.counter("colMismatch");
        this.errorCounter = metricGroup.counter("error");
    }

    /**
     * 解析如下消息:
     * head@col1,col2,col3\col4,col5,col6
     * 其中head@可能不存在
     *
     * 1. 判断是否需要16进制转换
     *    如果需要16进制转换，提前转换各个分隔符
     * 2. 判断是否带head
     *    如果head存在,则定位headSeparator位置,获取head和message body。并且判断head是否存在与headers中，存在则继续处理，不存在丢弃
     *    如果head不存在,将消息当成原始数据
     * 3. 判断消息分隔符messageSeparator
     *    如果消息分隔符按消息不是空的，按消息分割符分割成多行数据
     *    如果消息分隔符按消息是空的，不处理
     * 4. 判断列分隔符separator
     *    如果列分隔符按消息不是空的，按消息分割符分割成多列数据
     *    如果列分隔符按消息是空的，按照单行处理
     * */
    @Override
    public void flatMap(Row in, Collector<Row> out) throws Exception {
        try {
            inputCounter.inc();
            String originMsg = in.getField(0).toString();
            if (log.isDebugEnabled()) {
                log.debug("receive msg : {}", originMsg);
            }

            Message message = null;
            if (StringUtils.isNotEmpty(headerSeparator)) {
                message = extractMessage(originMsg, headers, headerSeparator);
                if (message == null) {
                    return;
                }
            } else {
                message = new Message(originMsg);
            }

            collect(originMsg, message, out);

        } catch (Exception e) {
            log.error("parser message {} throw exception", in, e);
            errorCounter.inc();
        }
    }

    /**
     * 截掉消息头，返回消息体
     *
     * @param message         消息
     * @param headers         消息头
     * @param headerSeparator 消息头分隔符
     * @return 消息体
     */
    private Message extractMessage(String message, String[] headers, String headerSeparator) {
        Message msg = null;
        int index = StringUtils.indexOf(message, headerSeparator);
        if (index > -1) {
            String msgHead = message.substring(0, index);
            String msgBody = message.substring(index + headerSeparator.length());
            if (ArrayUtils.isNotEmpty(headers)) {
                if (ArrayUtils.contains(headers, msgHead)) {
                    msg = new Message(msgHead, msgBody);
                } else {
                    headerMismatchCounter.inc();
                    log.debug("skip message {} , because head {} not in [{}]",
                            message, msgHead, StringUtils.join(headers, ','));
                }
            } else {
                msg = new Message(msgHead, msgBody);
            }

        } else {
            log.debug("message not contains headerSeparator:[{}], please check config", headerSeparator);
        }
        return msg;
    }

    /**
     * @param sourceMsg 原始数据
     * @param message
     * @param collector
     */
    private void collect(String sourceMsg, Message message, Collector<Row> collector) {
        String header = message.getHeader();
        String msgBody = message.getBody();
        if (StringUtils.isNotEmpty(messageSeparator)) {
            /*
             * 处理多行数据
             * */
            String[] rows = msgBody.split(messageSeparator, -1);
            if (log.isDebugEnabled()) {
                log.debug("Message contains {} records", rows.length);
            }
            for (String rowStr : rows) {
                collectSingleRow(sourceMsg, header, rowStr, collector);
            }
        } else {
            /*
             * 处理单行数据
             * */
            collectSingleRow(sourceMsg, header, msgBody, collector);
        }

    }

    /**
     * 处理单行数据
     *
     * @param sourceMsg 原始数据
     * @param header    消息头
     * @param msgBody   消息体
     * @param collector
     */
    private void collectSingleRow(String sourceMsg, String header, String msgBody, Collector<Row> collector) {
        String[] columns;
        if (StringUtils.isNotBlank(separator)) {
            columns = msgBody.split(separator, -1);
        } else {
            columns = new String[]{msgBody};
        }

        if (!validateMessage(sourceMsg, header, columns)) {
            return;
        }

        String[] colsWithHeader = new String[arity];
        if (StringUtils.isNotEmpty(header)) {
            colsWithHeader[0] = header;
            System.arraycopy(columns, 0, colsWithHeader, 1, arity - 1);
        } else {
            System.arraycopy(columns, 0, colsWithHeader, 0, arity);
        }
        for (int i = 0; i < colsWithHeader.length; i++) {
            String column = colsWithHeader[i];
            String type = outRowTypeInfo.getTypeAt(i).toString();
            if ("null".equalsIgnoreCase(column) || "".equalsIgnoreCase(column)) {
                row.setField(i, ConvertUtil.castNull(type));
            } else {
                row.setField(i, ConvertUtil.convert(column, type));
            }
        }
        log.debug("applying raw data {}", row);
        collector.collect(row);
        outputCounter.inc();
    }

    /**
     * 校验数据字段数是否匹配
     *
     * @param sourceMsg
     * @param header
     * @param columns
     * @return
     */
    private boolean validateMessage(String sourceMsg, String header, String[] columns) {
        if (!ignoreCheckFieldSize) {
            if (StringUtils.isEmpty(header) && columns.length != arity) {
                logValidateMessage(columns.length, sourceMsg, StringUtils.join(columns, ";"));
                return false;
            } else if (StringUtils.isNotEmpty(header) && columns.length + 1 != arity) {
                logValidateMessage(columns.length + 1, sourceMsg, StringUtils.join(columns, ";"));
                return false;
            }
        } else {
            if (StringUtils.isEmpty(header) && columns.length < arity) {
                logValidateMessage(columns.length, sourceMsg, StringUtils.join(columns, ";"));
                return false;
            } else if (StringUtils.isNotEmpty(header) && columns.length + 1 < arity) {
                logValidateMessage(columns.length + 1, sourceMsg, StringUtils.join(columns, ";"));
                return false;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(Arrays.toString(columns));
        }
        return true;
    }

    private void logValidateMessage(int gotSize, String sourceMsg, String columns) {
        colMismatchCounter.inc();
        log.error("Length is not enough! The Number of fields containing header is {}, but got {}", arity, gotSize);
        log.error("Skip message {}, separator is {}, because the number of fields does not match.", sourceMsg, separator);
        log.error("split data is {}", columns);
    }

    /**
     * 十六进制数据转换成字符串
     *
     * @param source 十六进制数据
     * @return 字符串
     */
    private String hexToStr(String source) {
        if (StringUtils.isEmpty(source)) {
            return null;
        }
        int len = source.length();
        byte[] ret = new byte[len >>> 1];
        for (int i = 0; i <= len - 2; i += 2) {
            ret[i >>> 1] = (byte) (Integer.parseInt(source.substring(i, i + 2).trim(), 16) & 0xff);
        }

        return new String(ret);
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    static class Message implements Serializable {

        private String header;

        private String body;

        public Message(String body) {
            this.body = body;
        }
    }
}
