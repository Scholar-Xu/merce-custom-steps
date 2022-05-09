package com.inforefiner.custom.util;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.api.java.typeutils.TypeExtractor;
import org.apache.flink.types.Row;

import java.io.Serializable;

public class RecordUtil implements Serializable {

    private static final long serialVersionUID = -5949733467070727225L;

    // record type methods
    public static RowTypeInfo buildTypeInfo(TypeInformation<?>[] types, String[] fieldNames) {
        return new RowTypeInfo(types, fieldNames);
    }

    public static RowTypeInfo extend(RowTypeInfo typeInfo, TypeInformation<?>[] types, String[] fieldNames) {
        TypeInformation<?>[] _types = ArrayUtils.addAll(typeInfo.getFieldTypes(), types);
        String[] _fieldNames = ArrayUtils.addAll(typeInfo.getFieldNames(), fieldNames);
        return new RowTypeInfo(_types, _fieldNames);
    }

    public static RowTypeInfo extend(RowTypeInfo typeInfo, String... fieldNames) {
        TypeInformation<?>[] types = new TypeInformation[fieldNames.length];
        for (int i = 0; i < types.length; i += 1) {
            types[i] = TypeExtractor.getForClass(Object.class);
        }
        return extend(typeInfo, types, fieldNames);
    }

    public static int[] getFieldsIndex(RowTypeInfo rowTypeInfo, String... fieldNames) {
        int[] ret = new int[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            ret[i] = rowTypeInfo.getFieldIndex(fieldNames[i]);
        }
        return ret;
    }

    public static <X> TypeInformation<X>[] getTypesAt(RowTypeInfo rowTypeInfo, String... fieldNames) {
        TypeInformation<X>[] typeInfos = new TypeInformation[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            int idx = rowTypeInfo.getFieldIndex(fieldNames[i]);
            typeInfos[i] = rowTypeInfo.getTypeAt(idx);
        }
        return typeInfos;
    }

    // record methods
    public static Object[] getFields(Row row, int... arr) {
        if (arr.length == 0) {
            throw new IllegalArgumentException("get fields without field index");
        }
        Object[] ret = new Object[arr.length];
        for (int i = 0; i < arr.length; i++) {
            ret[i] = row.getField(arr[i]); // ret[i] = row.getField(arr[i]);
        }
        return ret;
    }

    public static Row extend(Row row, Object... values) {
        if (values == null || values.length == 0)
            return row;
        int len = row.getArity();
        Object[] arr = new Object[len + values.length];
        for (int i = 0; i < len; i++) {
            arr[i] = row.getField(i);
        }
        for (int i = 0; i < values.length; i++) {
            arr[len + i] = values[i];
        }
        return Row.of(arr);
    }

    public static String mkString(Row row, String separator, String nullValue) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.getArity(); i++) {
            if (i > 0) {
                sb.append(separator);
            }
            Object r = row.getField(i);
            if (r == null) {
                r = nullValue;
            } else if (r instanceof Double && (Double.isInfinite((double) r) || Double.isNaN((double) r))) {
                r = nullValue;
            }
            sb.append(r);
        }
        return sb.toString();
    }
}
