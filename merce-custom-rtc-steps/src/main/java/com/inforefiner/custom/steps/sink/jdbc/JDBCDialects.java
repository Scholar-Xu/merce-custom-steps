package com.inforefiner.custom.steps.sink.jdbc;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class JDBCDialects {

    private static final List<JDBCDialect> DIALECTS = Arrays.asList(
            new SnowballDialect(),
            new OracleDialect()
    );

    public static Optional<JDBCDialect> get(String url) {
        for (JDBCDialect dialect : DIALECTS) {
            if (dialect.canHandle(url)) {
                return Optional.of(dialect);
            }
        }
        return Optional.empty();
    }


    private static class SnowballDialect implements JDBCDialect {

        @Override
        public boolean canHandle(String url) {
            return url.startsWith("jdbc:snowball:");
        }

        @Override
        public String buildQuery(String table, String[] fields) {
            String[] marks = new String[fields.length];
            Arrays.fill(marks, "?");
            return "insert into " + table + " (" + StringUtils.join(fields, ",") +
                    ") values (" + StringUtils.join(marks, ",") + ")";
        }

        @Override
        public String buildUpdate(String table) {
            String update = String.format("ALTER TABLE %s UPDATE %s WHERE %s", table, "%s", "%s");
            return update;
        }

        @Override
        public String buildDelete(String table) {
            String delete = String.format("ALTER TABLE %s DELETE WHERE %s", table, "%s");
            return delete;
        }

        @Override
        public String buildInsert(String table, String[] fields) {
            String delete = String.format("INSERT INTO %s (%s) values (%s)", table, StringUtils.join(fields, ","), "%s");
            return delete;
        }

        @Override
        public String quoteIdentifier(Object value, int type) {
            switch (type) {
                case java.sql.Types.CHAR:
                case java.sql.Types.NCHAR:
                case java.sql.Types.VARCHAR:
                case java.sql.Types.LONGVARCHAR:
                case java.sql.Types.LONGNVARCHAR:
                case java.sql.Types.DATE:
                    return "'" + value.toString() + "'";
                case java.sql.Types.TIMESTAMP:
                    return "'" + value.toString().substring(0, 19) + "'";
                case java.sql.Types.BOOLEAN:
                    return (boolean) value ? "1" : "0";
                default:
                    return value.toString();
            }
        }

        @Override
        public String quoteFieldName(String fieldName) {
            return fieldName;
        }
    }

    private static class OracleDialect implements JDBCDialect {

        @Override
        public boolean canHandle(String url) {
            return url.startsWith("jdbc:oracle:");
        }

        @Override
        public String buildQuery(String table, String[] fields) {
            String[] marks = new String[fields.length];
            Arrays.fill(marks, "?");
            String[] quoteFieldNames = Arrays.stream(fields).map(f -> quoteFieldName(f)).toArray(String[]::new);
            return "insert into \"" + table + "\" (" + StringUtils.join(quoteFieldNames, ",") +
                    ") values (" + StringUtils.join(marks, ",") + ")";
        }

        @Override
        public String buildUpdate(String table) {
            String update = String.format("UPDATE \"%s\" SET %s WHERE %s", table, "%s", "%s");
            return update;
        }

        @Override
        public String buildDelete(String table) {
            String delete = String.format("DELETE \"%s\" WHERE %s", table, "%s");
            return delete;
        }

        @Override
        public String buildInsert(String table, String[] fields) {
            String delete = String.format("INSERT INTO \"%s\" (%s) values (%s)", table, StringUtils.join(fields, ","), "%s");
            return delete;
        }

        @Override
        public String quoteIdentifier(Object value, int type) {
            switch (type) {
                case java.sql.Types.CHAR:
                case java.sql.Types.NCHAR:
                case java.sql.Types.VARCHAR:
                case java.sql.Types.LONGVARCHAR:
                case java.sql.Types.LONGNVARCHAR:
                    return "'" + value.toString() + "'";
                case java.sql.Types.DATE:
                    return "TO_DATE('" + value.toString() + "', 'YYYY-MM-DD')";
                case java.sql.Types.TIMESTAMP:
                    return "TO_TIMESTAMP('" + value.toString() + "', 'YYYY-MM-DD HH24:MI:SS.FF')";
                case java.sql.Types.BOOLEAN:
                    return (boolean) value ? "1" : "0";
                default:
                    return value.toString();
            }
        }

        @Override
        public String quoteFieldName(String fieldName) {
            return "\"" + fieldName + "\"";
        }
    }
}
