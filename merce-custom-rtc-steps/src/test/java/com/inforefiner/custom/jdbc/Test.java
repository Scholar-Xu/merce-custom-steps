package com.inforefiner.custom.jdbc;

import java.sql.*;

public class Test {

    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        Class.forName("com.inforefiner.snowball.SnowballDriver");
        Connection connection = DriverManager.getConnection("jdbc:snowball://192.168.1.89:8123/lqr", "default", "");

        PreparedStatement preparedStatement = connection.prepareStatement("insert into shiy_custom_jdbc_sink (`action`,`table`,`action_time`,`id`,`name`,`random_id`) values (?,?,?,?,?,?)");
        preparedStatement.setString(1, "I");
        preparedStatement.setString(2, "table6");
        preparedStatement.setString(3, "2021-04-09 10:57:22");
        preparedStatement.setString(4, "70");
        preparedStatement.setString(5, "userF");
        preparedStatement.setString(6, "243af1ae-ddec-4baa-8ed3-7c71a4f91587");
        preparedStatement.addBatch();

        preparedStatement.setString(1, "I");
        preparedStatement.setString(2, "table7");
        preparedStatement.setString(3, "2021-04-09 10:57:22");
        preparedStatement.setString(4, "71");
        preparedStatement.setString(5, "userG");
        preparedStatement.setString(6, "243af1ae-ddec-4baa-8ed3-7c71a4f91587");
        preparedStatement.addBatch();

        preparedStatement.setString(1, "I");
        preparedStatement.setString(2, "table8");
        preparedStatement.setString(3, "2021-04-09 10:57:22");
        preparedStatement.setString(4, "72");
        preparedStatement.setString(5, "userH");
        preparedStatement.setString(6, "243af1ae-ddec-4baa-8ed3-7c71a4f91587");
        preparedStatement.addBatch();

        preparedStatement.executeBatch();
        preparedStatement.close();

        connection.close();
    }

}
