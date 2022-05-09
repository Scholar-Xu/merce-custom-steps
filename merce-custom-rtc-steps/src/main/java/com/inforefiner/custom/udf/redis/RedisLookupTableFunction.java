package com.inforefiner.custom.udf.redis;

import com.inforefiner.custom.plugins.redis.RedisUtil;
import com.inforefiner.custom.plugins.redis.config.FlinkJedisConfigAdapter;
import com.inforefiner.custom.plugins.redis.config.FlinkJedisConfigBase;
import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainer;
import com.inforefiner.custom.plugins.redis.container.RedisCommandsContainerBuilder;
import com.inforefiner.custom.util.AesCryptor;
import com.inforefiner.custom.util.ConvertUtil;
import com.inforefiner.custom.util.RecordUtil;
import com.merce.woven.annotation.Select;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import com.merce.woven.annotation.UDF;
import com.merce.woven.common.SchemaMiniDesc;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.types.Row;

import java.util.*;

/**
 * Created by P0007 on 2019/9/9.
 * 以下参数带{@link Setting}的，在前端配置好参数后，运行时可以直接用
 */
@Slf4j
@UDF(name = "REDIS_LOOKUP")
@Getter
@Setter
public class RedisLookupTableFunction extends TableFunction<Row> {

    /**
     * 表示选择一个元数据schema,如果需要整个Schema对象信息，可在下面利用参数
     * {@link SchemaMiniDesc} schema获取到，前端会将schema对象放入整个schema参数中
     */
    @Setting(selectType = SelectType.SCHEMA)
    private String schemaName;

    private SchemaMiniDesc schema;

    @Setting(description = "表名称")
    private String functionName;

    @Setting(description = "Redis URL")
    private String url;

    @Setting(description = "Redis Password")
    private String password;

    @Setting(description = "Redis Table")
    private String table;

    @Setting(description = "key字段", select = Select.INPUT, selectType = SelectType.FIELDS)
    private String key;

    @Setting(description = "表字段")
    private TableColumn[] schemaFields;

    @Setting(defaultValue = "0", description = "redis database", advanced = true, required = false)
    private String database = "0";

    @Setting(defaultValue = "2000", description = "redis连接和响应超时时间", advanced = true, required = false)
    private String timeout = "2000";

    @Setting(defaultValue = "8", description = "redis最大连接数", advanced = true, required = false)
    private String maxTotal = "8";

    @Setting(defaultValue = "8", description = "redis最大空闲连接数", advanced = true, required = false)
    private String maxIdle = "8";

    @Setting(defaultValue = "0", description = "redis最小空闲连接数", advanced = true, required = false)
    private String minIdle = "0";

    @Setting(defaultValue = "5", description = "redis重定向次数(集群模式有效)", advanced = true, required = false)
    private String maxRedirections = "5";

    private int keyIndex;

    private RedisCommandsContainer redisCommandsContainer;

    private RowTypeInfo rowTypeInfo;
    private Row row;

    @Override
    public void open(FunctionContext context) throws Exception {
        try {
            if (schemaFields.length == 0) {
                throw new RuntimeException("Schema fields is empty.");
            }
            this.rowTypeInfo = generateRowTypeInfo();
            int arity = this.rowTypeInfo.getArity();
            this.keyIndex = rowTypeInfo.getFieldIndex(key);
            row = new Row(arity);

            String passwd = StringUtils.isEmpty(password) ? null : password;
            String passwordDecrypt = new AesCryptor().decrypt(passwd);
            String[] arr = url.split(":");
            int _database = Integer.parseInt(database);
            int _timeout = Integer.parseInt(timeout);
            int _maxTotal = Integer.parseInt(maxTotal);
            int _maxIdle = Integer.parseInt(maxIdle);
            int _minIdle = Integer.parseInt(minIdle);
            int _maxRedirections = Integer.parseInt(maxRedirections);

            log.info("Redis client configurations in additionalKey {}:\n" +
                            "database: {}\ntimeout: {}\nmaxTotal: {}\nmaxIdle: {}\nminIdle: {}\nmaxRedirections: {}",
                    table, database, timeout, maxTotal, maxIdle, minIdle, maxRedirections);
            FlinkJedisConfigAdapter jedisConfigAdapter = new FlinkJedisConfigAdapter.Builder()
                    .setHost(arr[0])
                    .setPort(Integer.parseInt(arr[1]))
                    .setPassword(passwordDecrypt)
                    .setDatabase(_database)
                    .setTimeout(_timeout)
                    .setMaxTotal(_maxTotal)
                    .setMaxIdle(_maxIdle)
                    .setMinIdle(_minIdle)
                    .setMaxRedirections(_maxRedirections)
                    .build();

            FlinkJedisConfigBase flinkJedisConfig = jedisConfigAdapter.getFlinkJedisConfig();
            RedisCommandsContainer redisCommandsContainer = RedisCommandsContainerBuilder.build(flinkJedisConfig);
            this.redisCommandsContainer = redisCommandsContainer;
            this.redisCommandsContainer.open();
        } catch (Exception e) {
            log.error("Redis has not been properly initialized: ", e);
            throw e;
        }
    }

    /**
     * UDF主要函数,每条数据都用调用
     * @param key
     */
    public void eval(String key) {
        String rediesKey = RedisUtil.rediesKey(table, key);
        Map<String, String> value = redisCommandsContainer.hgetAll(rediesKey);
        String[] fieldNames = rowTypeInfo.getFieldNames();
        for (int i = 0; i < fieldNames.length; i++) {
            String filedType = rowTypeInfo.getTypeAt(i).toString();
            String valueStr;
            if (keyIndex == i) {
                valueStr = key;
            } else {
                valueStr = value.get(fieldNames[i]);
            }
            Object convertedValue = ConvertUtil.convert(valueStr, filedType);
            row.setField(i, convertedValue);
        }
        log.debug("Lookup key {}, collect row {}", key, row);
        collect(row);
    }

    @Override
    public void close() throws Exception {
        if (redisCommandsContainer != null) {
            redisCommandsContainer.close();
        }
    }

    @Override
    public TypeInformation<Row> getResultType() {
        return generateRowTypeInfo();
    }

    private RowTypeInfo generateRowTypeInfo() {
        String[] fieldNames = new String[schemaFields.length];
        String[] fieldTypes = new String[schemaFields.length];
        for (int j = 0; j < schemaFields.length; j++) {
            TableColumn schemaField = schemaFields[j];
            fieldNames[j] = schemaField.getName();
            fieldTypes[j] = schemaField.getType();
        }
        RowTypeInfo rowTypeInfo = RecordUtil.buildTypeInfo(ConvertUtil.toTypeInformations(fieldTypes), fieldNames);
        return rowTypeInfo;
    }
}