package com.inforefiner.custom.util;

import com.merce.woven.common.FieldDesc;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by DebugSy on 2019/8/14.
 */
public class StepFieldUtil implements Serializable {

    private static final long serialVersionUID = 8141077915202116813L;

    public static String[] getFieldNameArray(List<FieldDesc> fields) {
        int size = fields.size();
        String[] filedNames = new String[size];
        for (int i = 0; i < size; i++) {
            filedNames[i] = fields.get(i).getName();
        }
        return filedNames;
    }

    public static String[] getFieldTypeArray(List<FieldDesc> fields) {
        int size = fields.size();
        String[] types = new String[size];
        for (int i = 0; i < size; i++) {
            types[i] = fields.get(i).getType();
        }
        return types;
    }

    public static String[] getFieldAliasesArray(List<FieldDesc> fields) {
        int size = fields.size();
        String[] aliases = new String[size];
        for (int i = 0; i < size; i++) {
            String alias = fields.get(i).getAlias();
            String column = fields.get(i).getName();
            if (StringUtils.isNoneEmpty(alias)) {
                aliases[i] = alias;
            } else {
                aliases[i] = column;
            }
        }
        return aliases;
    }

    public static Map<String, String> getFieldChanged(List<FieldDesc> fields) {
        Map<String, String> result = new HashMap<>();
        for (FieldDesc field : fields) {
            if (field.changed()) {
                result.put(field.getAlias(), field.getName());
            }
        }
        return result;
    }

}
