package com.inforefiner.custom.udf.redis;

import com.merce.woven.annotation.Select;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import com.merce.woven.common.SchemaMiniDesc;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * Created by P0007 on 2020/1/2.
 */
@Getter
@Setter
@ToString
public class RedisTableRule implements Serializable {

    @Setting(selectType = SelectType.DATASET)
    private String datasetName;

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

    private SchemaMiniDesc schema;

}
