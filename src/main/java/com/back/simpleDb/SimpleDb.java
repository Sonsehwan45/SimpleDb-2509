package com.back.simpleDb;


import lombok.Setter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class SimpleDb {
    private final String url;
    private final String user;
    private final String password;
    @Setter
    private boolean devMode = false;



    public SimpleDb(String host, String user, String password, String dbName) {
        this.url = "jdbc:mysql://" + host + ":3306/" + dbName + "?serverTimezone=Asia/Seoul";
        this.user = user;
        this.password = password;
    }

    public Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url, user, password);
            if (devMode) {
                System.out.println("DB 연결 성공: " + url);
            }
            return conn;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try  {
            conn = connect();
            pstmt = conn.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            if (devMode) {
                System.out.println("개발모드 SQL 실행: " + sql);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }
}