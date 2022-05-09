package com.inforefiner.custom.util;


import com.merce.woven.common.FieldDesc;
import com.merce.woven.flow.spark.flow.StepValidateResult;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;

/**
 * @author P0007
 * @version 1.0
 * @date 2020/4/29 19:30
 */
public class CheckUtil implements Serializable {

    private static final long serialVersionUID = -1919605330744398010L;

    public static void checkingColumnExist(String setting, String column, List<FieldDesc> fields, StepValidateResult result) {
        String[] fieldAliasesArray = StepFieldUtil.getFieldAliasesArray(fields);
        if (!ArrayUtils.contains(fieldAliasesArray, column)) {
            result.addError(setting, "Setting[%s]. This field[%s] is not exist " +
                            "in the fields[%s]", setting, column,
                    StringUtils.join(fieldAliasesArray, ","));
        }
    }


    /**
     * 检查Long型参数设置是否正确
     * 1. 检查参数是否是空
     * 2. 检查是否能解析成long值
     * 3. 检查值是否是正整数
     * @param setting
     * @param longValue
     * @param result
     * @return
     */
    public static void checkingLong(String setting, String longValue, StepValidateResult result) {
        if (StringUtils.isEmpty(longValue)) {
            result.addError(setting,setting + " is empty");
        } else {
            try {
                long settingValue = Long.parseLong(longValue);
                if (settingValue <= 0) {
                    result.addError(setting,setting + " must be > 0");
                }
            } catch (Exception e) {
                result.addError(setting,setting + " setting is error");
            }
        }
    }

    /**
     * 检查Boolean类型参数设置是否正确
     * 1. 检查参数是否是空
     * 2. 检查是否能解析成boolean值
     * @param setting
     * @param booleanValue
     * @param result
     */
    public static void checkingBoolean(String setting, String booleanValue, StepValidateResult result) {
        if (StringUtils.isEmpty(booleanValue)) {
            result.addError(setting,setting + " is empty");
        } else {
            try {
                Boolean.parseBoolean(booleanValue);
            } catch (Exception e) {
                result.addError(setting,setting + " setting is error");
            }
        }
    }

    /**
     * 检查String类型参数设置是否正确
     * 1. 检查参数是否是空
     * @param setting
     * @param stringValue
     * @param result
     */
    public static void checkingString(String setting, String stringValue, StepValidateResult result) {
        if (StringUtils.isEmpty(stringValue)) {
            result.addError(setting,setting + " is empty");
        }
    }

    /**
     * 检查Inte类型参数设置是否正确
     * 1. 检查参数是否是空
     * 2. 检查是否能解析成boolean值
     * @param setting
     * @param intValue
     * @param result
     */
    public static void checkingInteger(String setting, String intValue, StepValidateResult result) {
        if (StringUtils.isEmpty(intValue)) {
            result.addError(setting, setting + " is empty");
        } else {
            try {
                Integer.parseInt(intValue);
            } catch (Exception e) {
                result.addError(setting,setting + " setting is error");
            }
        }
    }

}
