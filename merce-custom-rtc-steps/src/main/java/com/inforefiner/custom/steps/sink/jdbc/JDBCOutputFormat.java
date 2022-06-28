/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inforefiner.custom.steps.sink.jdbc;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingWindowReservoir;
import com.inforefiner.custom.util.ConvertUtil;
import com.inforefiner.custom.util.StepFieldUtil;
import com.merce.woven.common.FieldDesc;
import com.merce.woven.common.SchemaMiniDesc;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.apache.flink.connector.jdbc.utils.JdbcUtils.setRecordToStatement;

/**
 * OutputFormat to write Rows into a JDBC database.
 * The OutputFormat has to be configured using the supplied OutputFormatBuilder.
 *
 * @see Row
 * @see DriverManager
 */
public class JDBCOutputFormat extends AbstractJDBCOutputFormat<Row> {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(JDBCOutputFormat.class);

    private String drivername;
    private final String query; // PreparedStatement
    private String update; // Statement
    private String delete; // Statement
    private String insert; // Statement
    private RowTypeInfo rowTypeInfo;
    private SchemaMiniDesc schema;
    private int[] sqlTypes;
    private int[] insertColIdx;
    private final int batchInterval;
    private final int[] typesArray;
    private List<Row> cacheRows;

    private int actionColumnIdx;
    /*
    * 增加主键名称设置已满如如下情况:
    * 当主键字段既是where条件又是需要update的字段时，必须单独设置主键名称，否则update时获取不到实际的主键名称或者主键值
    * */
    private String[] primaryKeyNames;
    private int[] primaryKeyIdx;
    private PreparedStatement statement;
    private Statement upsertStatement;
    private int batchCount = 0;
    private JDBCDialect jdbcDialect;

    //metrics
    private transient Counter inputCnt;
    private transient Counter insertCnt;
    private transient Counter updateCnt;
    private transient Counter deleteCnt;
    private transient Counter affectedCnt;
    private transient DropwizardHistogramWrapper installTime;
    private transient DropwizardHistogramWrapper histogram;

    public JDBCOutputFormat(String username, String password, String drivername,
                            String dbURL, String query, String update, String delete, String insert,
                            RowTypeInfo rowTypeInfo, SchemaMiniDesc schema, int actionColumnIdx,
                            String[] primaryKeyNames,int[] primaryKeyIdx,
                            int batchInterval, int[] typesArray) {
        super(username, password, drivername, dbURL);
        this.query = query;
        this.update = update;
        this.delete = delete;
        this.insert = insert;
        this.rowTypeInfo = rowTypeInfo;
        this.schema = schema;
        this.actionColumnIdx = actionColumnIdx;
        this.primaryKeyNames = primaryKeyNames;
        this.primaryKeyIdx = primaryKeyIdx;
        this.batchInterval = batchInterval;
        this.typesArray = typesArray;
        this.drivername = drivername;
    }

    /**
     * Connects to the target database and initializes the prepared statement.
     *
     * @param taskNumber The number of the parallel instance.
     * @throws IOException Thrown, if the output could not be opened due to an
     *                     I/O problem.
     */
    @Override
    public void open(int taskNumber, int numTasks) throws IOException {
        try {
            Class.forName(drivername);
            establishConnection();
            statement = insertConnection.prepareStatement(query);
            upsertStatement = insertConnection.createStatement();
            this.cacheRows = new ArrayList<>();

            Optional<JDBCDialect> jdbcDialectOptional = JDBCDialects.get(dbURL);
            if (!jdbcDialectOptional.isPresent()) {
                throw new RuntimeException("Not support jdbc url " + dbURL);
            }
            this.jdbcDialect = jdbcDialectOptional.get();
        } catch (SQLException sqe) {
            throw new IllegalArgumentException("open() failed.", sqe);
        } catch (ClassNotFoundException cnfe) {
            throw new IllegalArgumentException("JDBC driver class not found.", cnfe);
        }

        this.sqlTypes = transformTypeToSqlType(schema);
        this.insertColIdx = extractedInsertColIndex(schema, rowTypeInfo);

        //metrics
        MetricGroup metricGroup = getRuntimeContext().getMetricGroup().addGroup("custom_group");
        inputCnt = metricGroup.counter("inputCnt");
        insertCnt = metricGroup.counter("insertCnt");
        updateCnt = metricGroup.counter("updateCnt");
        deleteCnt = metricGroup.counter("deleteCnt");
        affectedCnt = metricGroup.counter("affectedCnt");
        Histogram _installTime = new Histogram(new SlidingWindowReservoir(1000));
        Histogram _histogram = new Histogram(new SlidingWindowReservoir(1000));
        this.installTime = metricGroup.histogram("install_time", new DropwizardHistogramWrapper(_installTime));
        this.histogram = metricGroup.histogram("flush_time", new DropwizardHistogramWrapper(_histogram));

        LOG.debug("Print jdbc sink format information:");
        LOG.debug("rowTypeInformation {}", rowTypeInfo);
        LOG.debug("actionColumnIdx {}", actionColumnIdx);
        LOG.debug("primaryKeyIdx {}", Arrays.toString(primaryKeyIdx));
        LOG.debug("batchInterval {}", batchInterval);
    }

    @Override
    public void writeRecord(Row row) throws IOException {
        cacheRows.add(row);
        batchCount++;

        if (batchCount >= batchInterval) {
            // execute batch
            long startTime = System.nanoTime();
            flush();
            long endTime = System.nanoTime();
            LOG.info("flush use time {}", endTime - startTime);
            this.histogram.update(endTime - startTime);
        }
    }

    void flush() {
        try {

            if (cacheRows.isEmpty()) {
                return;
            }

            if (!insertConnection.isValid(5)) {
                LOG.warn("DB connection timeout.");
                establishConnection();
                statement = insertConnection.prepareStatement(query);
                upsertStatement = insertConnection.createStatement();
            }
            String[] fieldNames = schema.fetchAllFieldsNames();
            List<String> upsertSqls = new ArrayList<>();
            for (Row row : cacheRows) {
                long installStartTime = System.currentTimeMillis();
                LOG.debug("apply row {}", row);
                Object action = row.getField(actionColumnIdx);
                if ("U".equals(action)) {
                    StringBuilder updateBuilder = new StringBuilder();
                    for (int i = 0; i < fieldNames.length; i++) {
                        if (ArrayUtils.contains(primaryKeyIdx, i)) {
                            continue;
                        }
                        Object value = row.getField(i);
                        if (value != null) {
                            if (updateBuilder.length() > 0) {
                                updateBuilder.append(",");
                            }
                            int type = typesArray[i];
                            String identifierValue = jdbcDialect.quoteIdentifier(value, type);
                            String fieldName = jdbcDialect.quoteFieldName(fieldNames[i]);
                            updateBuilder.append(fieldName + "=" + identifierValue);
                        } else {
                            //skip
                        }
                    }
                    String[] conditions = new String[primaryKeyIdx.length];
                    for (int i = 0; i < primaryKeyIdx.length; i++) {
                        int index = primaryKeyIdx[i];
                        Object primaryKeyObj = row.getField(index);
                        if (primaryKeyObj == null) {
                            throw new RuntimeException("primary key is null. index is " + index + ",row is "+ row);
                        }
                        String primaryKey = primaryKeyObj.toString();
                        int type = typesArray[index];
                        String identifierPrimaryKey = jdbcDialect.quoteIdentifier(primaryKey, type);
                        String primaryKeyName = jdbcDialect.quoteFieldName(primaryKeyNames[i]);
                        String condition = primaryKeyName + "=" + identifierPrimaryKey;
                        conditions[i] = condition;
                    }
                    String updateConditions = StringUtils.join(conditions, " AND ");
                    String updateSql = String.format(update, updateBuilder.toString(), updateConditions);
                    LOG.debug("update sql is {}", updateSql);
                    updateCnt.inc();
                    upsertSqls.add(updateSql);
                } else if ("D".equals(action)) {
                    String[] conditions = new String[primaryKeyIdx.length];
                    for (int i = 0; i < primaryKeyIdx.length; i++) {
                        int index = primaryKeyIdx[i];
                        Object primaryKeyObj = row.getField(index);
                        if (primaryKeyObj == null) {
                            throw new RuntimeException("primary key is null. index is " + index + ",row is "+ row);
                        }
                        String primaryKey = primaryKeyObj.toString();
                        int type = typesArray[index];
                        String identifierPrimaryKey = jdbcDialect.quoteIdentifier(primaryKey, type);
                        String primaryKeyName = jdbcDialect.quoteFieldName(primaryKeyNames[i]);
                        String condition = primaryKeyName + "=" + identifierPrimaryKey;
                        conditions[i] = condition;
                    }
                    String deleteConditions = StringUtils.join(conditions, " AND ");
                    String deleteSql = String.format(delete, deleteConditions);
                    LOG.debug("delete sql is {}", deleteSql);
                    deleteCnt.inc();
                    upsertSqls.add(deleteSql);
                } else if ("I".equals(action)) {
                    int schemaFieldSize = schema.getFields().size();
                    if (schemaFieldSize != row.getArity()) {
                        //部分字段用于设置主键，不需要输出
                        Row project = Row.project(row, insertColIdx);
                        setRecordToStatement(statement, sqlTypes, project);
                    } else {
                        setRecordToStatement(statement, sqlTypes, row);
                    }
                    statement.addBatch();
                    insertCnt.inc();
                } else {
                    LOG.error("Not support action {}. row is {}", action, row);
                }
                long installEndTime = System.currentTimeMillis();
                this.installTime.update(installEndTime - installStartTime);
            }
            inputCnt.inc(cacheRows.size());
            int[] batch = statement.executeBatch();
            for (String upsertSql : upsertSqls) {
                upsertStatement.execute(upsertSql);
            }
            int affectedRow = Arrays.stream(batch).sum();
            affectedCnt.inc(affectedRow);
            LOG.debug("JDBC batch flush, insert action affected rows is {}", affectedRow);
            cacheRows.clear();
            upsertSqls.clear();
            batchCount = 0;
        } catch (SQLException e) {
            throw new RuntimeException("Execution of JDBC statement failed.", e);
        }
    }

    private int[] transformTypeToSqlType(SchemaMiniDesc schema) {
        List<FieldDesc> schemaFields = schema.getFields();
        String[] types = StepFieldUtil.getFieldTypeArray(schemaFields);
        int[] sqlTypes = new int[types.length];
        for (int i = 0; i < sqlTypes.length; i++) {
            sqlTypes[i] = ConvertUtil.toSqlType(types[i]);
        }
        return sqlTypes;
    }

    private int[] extractedInsertColIndex(SchemaMiniDesc schema, RowTypeInfo rowTypeInfo) {
        List<FieldDesc> schemaFields = schema.getFields();
        String[] fieldNames = rowTypeInfo.getFieldNames();
        int schemaFieldSize = schemaFields.size();
        int[] insertColIdx = new int[schemaFieldSize];
        int k = 0;
        for (int j = 0; j < schemaFieldSize; j++) {
            FieldDesc fieldDesc = schemaFields.get(j);
            String name = StringUtils.isBlank(fieldDesc.getAlias()) ?
                    fieldDesc.getName() : fieldDesc.getAlias();
            for (int i = 0; i < fieldNames.length; i++) {
                String fieldName = fieldNames[i];
                if (fieldName.equals(name)) {
                    insertColIdx[k++] = i;
                    break;
                }
            }
        }
        return insertColIdx;
    }

    int[] getTypesArray() {
        return typesArray;
    }

    /**
     * Executes prepared statement and closes all resources of this instance.
     *
     * @throws IOException Thrown, if the input could not be closed properly.
     */
    @Override
    public void close() throws IOException {
        if (statement != null) {
            flush();
            try {
                statement.close();
            } catch (SQLException e) {
                LOG.info("JDBC statement could not be closed: " + e.getMessage());
            } finally {
                statement = null;
            }
        }

        closeDbConnection();
    }

    public static JDBCOutputFormatBuilder buildJDBCOutputFormat() {
        return new JDBCOutputFormatBuilder();
    }

    /**
     * Builder for a {@link JDBCOutputFormat}.
     */
    public static class JDBCOutputFormatBuilder {
        private String username;
        private String password;
        private String drivername;
        private String dbURL;
        private String query;
        private String update;
        private String delete;
        private String insert;
        private RowTypeInfo rowTypeInfo;
        private SchemaMiniDesc schema;
        private int actionColumnIdx;
        private String[] primaryKeyNames;
        private int[] primaryKeyIdx;
        private int batchInterval = DEFAULT_FLUSH_MAX_SIZE;
        private int[] typesArray;

        protected JDBCOutputFormatBuilder() {
        }

        public JDBCOutputFormatBuilder setUsername(String username) {
            this.username = username;
            return this;
        }

        public JDBCOutputFormatBuilder setPassword(String password) {
            this.password = password;
            return this;
        }

        public JDBCOutputFormatBuilder setDrivername(String drivername) {
            this.drivername = drivername;
            return this;
        }

        public JDBCOutputFormatBuilder setDBUrl(String dbURL) {
            this.dbURL = dbURL;
            return this;
        }

        public JDBCOutputFormatBuilder setQuery(String query) {
            this.query = query;
            return this;
        }

        public JDBCOutputFormatBuilder setUpdate(String update) {
            this.update = update;
            return this;
        }

        public JDBCOutputFormatBuilder setDelete(String delete) {
            this.delete = delete;
            return this;
        }

        public JDBCOutputFormatBuilder setInsert(String insert) {
            this.insert = insert;
            return this;
        }

        public JDBCOutputFormatBuilder setRowTypeInfo(RowTypeInfo rowTypeInfo) {
            this.rowTypeInfo = rowTypeInfo;
            return this;
        }

        public JDBCOutputFormatBuilder setSchema(SchemaMiniDesc schema) {
            this.schema = schema;
            return this;
        }

        public JDBCOutputFormatBuilder setActionColumnIdx(int actionColumnIdx) {
            this.actionColumnIdx = actionColumnIdx;
            return this;
        }

        public JDBCOutputFormatBuilder setPrimaryKeyNames(String[] primaryKeyNames) {
            this.primaryKeyNames = primaryKeyNames;
            return this;
        }

        public JDBCOutputFormatBuilder setPrimaryKeyIdx(int[] primaryKeyIdx) {
            this.primaryKeyIdx = primaryKeyIdx;
            return this;
        }

        public JDBCOutputFormatBuilder setBatchInterval(int batchInterval) {
            this.batchInterval = batchInterval;
            return this;
        }

        public JDBCOutputFormatBuilder setSqlTypes(int[] typesArray) {
            this.typesArray = typesArray;
            return this;
        }

        /**
         * Finalizes the configuration and checks validity.
         *
         * @return Configured JDBCOutputFormat
         */
        public JDBCOutputFormat finish() {
            if (this.username == null) {
                LOG.info("Username was not supplied.");
            }
            if (this.password == null) {
                LOG.info("Password was not supplied.");
            }
            if (this.dbURL == null) {
                throw new IllegalArgumentException("No database URL supplied.");
            }
            if (this.query == null) {
                throw new IllegalArgumentException("No query supplied.");
            }
            if (this.drivername == null) {
                throw new IllegalArgumentException("No driver supplied.");
            }

            return new JDBCOutputFormat(
                    username, password, drivername, dbURL,
                    query, update, delete, insert,
                    rowTypeInfo, schema, actionColumnIdx,
                    primaryKeyNames, primaryKeyIdx,
                    batchInterval, typesArray);
        }
    }

}
