package com.back.article.db;

import java.sql.*;

public class SimpleDb {
    private final String host;
    private final String user;
    private final String password;
    private final String dbName;
    private boolean devMode = false;

    public SimpleDb(String host, String user, String password, String dbName) {
        this.host = host;
        this.user = user;
        this.password = password;
        this.dbName = dbName;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    // DB 연결
    public Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":3306/" + dbName + "?serverTimezone=Asia/Seoul";
        return DriverManager.getConnection(url, user, password);
    }

    // 단순 SQL 실행 (DDL, DML)
    public void run(String sql, Object... params) {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = getConnection();
            pstmt = conn.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            pstmt.executeUpdate();

            if (devMode) {
                System.out.println("[DEBUG] SQL 실행: " + sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) {
                try {
                    pstmt.close();
                } catch (SQLException ignored) {}
            }
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException ignored) {}
            }
        }
    }

    // Sql 객체 생성
    public Sql genSql() {
        return new Sql(this);
    }
}
