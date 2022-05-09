package com.inforefiner.custom.plugins.redis;

public class RedisUtil {

    public static final String REDIS_COLUMN_SEPARATOR = "_";

    public static final String REDIS_TABLE_SEPARATOR = ":";

    public static void checkArgument(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public static String rediesKey(String additionalKey, String key) {
        return additionalKey + ":" + key;
    }
}
