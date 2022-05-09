package com.inforefiner.custom.steps.sink.jdbc;

public interface JDBCDialect {

    boolean canHandle(String url);

    String buildQuery(String table, String[] fields);

    String buildUpdate(String table);

    String buildDelete(String table);

    String buildInsert(String table, String[] fields);

    /**
     *
     * @param value
     * @param type
     * @return
     */
    String quoteIdentifier(Object value, int type);

    String quoteFieldName(String fieldName);

}
