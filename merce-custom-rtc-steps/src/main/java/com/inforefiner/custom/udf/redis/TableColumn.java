package com.inforefiner.custom.udf.redis;

import com.merce.woven.annotation.Select;
import com.merce.woven.annotation.SelectType;
import com.merce.woven.annotation.Setting;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Created by P0007 on 2020/1/2.
 */
@Getter
@Setter
public class TableColumn implements Serializable {

    @Setting(description = "value字段名称", select = Select.SCHEMA, selectType = SelectType.FIELDS)
    private String name;

    @Setting(description = "value字段类型")
    private String type;

}
