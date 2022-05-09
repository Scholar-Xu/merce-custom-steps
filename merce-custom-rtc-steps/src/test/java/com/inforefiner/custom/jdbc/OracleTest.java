package com.inforefiner.custom.jdbc;

import java.math.BigDecimal;
import java.sql.*;

public class OracleTest {

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        Connection connection = DriverManager.getConnection("jdbc:oracle:thin:@192.168.1.82:1521:orcl", "carpo", "123456");

        PreparedStatement preparedStatement = connection.prepareStatement("insert into \"shiy_all_datatype_sink\" (\"int_col\",\"string_col\",\"boolean_col\",\"byte_col\",\"timestamp_col\",\"date_col\",\"decimal_col\",\"float_col\",\"double_col\",\"long_col\",\"short_col\",\"action_col\") values (?,?,?,?,?,?,?,?,?,?,?,?)");
        Statement statement = connection.createStatement();

        preparedStatement.setInt(1, 6);
        preparedStatement.setString(2, "test6");
        preparedStatement.setInt(3, 0);
        preparedStatement.setByte(4, (byte) 127);
        preparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
        preparedStatement.setDate(6, new Date(System.currentTimeMillis()));
        preparedStatement.setBigDecimal(7, new BigDecimal(0.1111111));
        preparedStatement.setFloat(8, 0.22F);
        preparedStatement.setDouble(9, 0.33D);
        preparedStatement.setLong(10, 123456L);
        preparedStatement.setShort(11, (short) 12);
        preparedStatement.setString(12, "action");
        preparedStatement.addBatch();

        preparedStatement.setInt(1, 7);
        preparedStatement.setString(2, "test7");
        preparedStatement.setInt(3, 0);
        preparedStatement.setByte(4, (byte) 127);
        preparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
        preparedStatement.setDate(6, new Date(System.currentTimeMillis()));
        preparedStatement.setBigDecimal(7, new BigDecimal(0.1111111));
        preparedStatement.setFloat(8, 0.22F);
        preparedStatement.setDouble(9, 0.33D);
        preparedStatement.setLong(10, 123456L);
        preparedStatement.setShort(11, (short) 12);
        preparedStatement.setString(12, "action");
        preparedStatement.addBatch();
        preparedStatement.executeBatch();

        statement.execute("UPDATE \"shiy_all_datatype_sink\" SET \"string_col\"='U',\"boolean_col\"=0,\"byte_col\"=127,\"timestamp_col\"=TO_TIMESTAMP('2021-05-19 12:05:35.455', 'YYYY-MM-DD HH24:MI:SS.FF'),\"date_col\"=TO_DATE('2021-05-19', 'YYYY-MM-DD'),\"decimal_col\"=0.357000000000000000,\"float_col\"=0.54164916,\"double_col\"=0.93495035,\"long_col\"=5514781225772493975,\"short_col\"=32767,\"action_col\"='U' WHERE \"int_col\"=6");

        preparedStatement.setInt(1, 8);
        preparedStatement.setString(2, "test8");
        preparedStatement.setInt(3, 0);
        preparedStatement.setByte(4, (byte) 127);
        preparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
        preparedStatement.setDate(6, new Date(System.currentTimeMillis()));
        preparedStatement.setBigDecimal(7, new BigDecimal(0.1111111));
        preparedStatement.setFloat(8, 0.22F);
        preparedStatement.setDouble(9, 0.33D);
        preparedStatement.setLong(10, 123456L);
        preparedStatement.setShort(11, (short) 12);
        preparedStatement.setString(12, "action");
        preparedStatement.addBatch();

        preparedStatement.executeBatch();
        preparedStatement.close();

        connection.close();
    }

}
