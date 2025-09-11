package com.back.article.db;

import java.sql.*;

/**
 * DB 연결 및 SQL 실행을 담당하는 클래스
 *
 * 👉 DB 연결(Connection) 생성, 단순 SQL 실행, Sql 빌더 객체 제공 기능을 지원합니다.
 */
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

    /**
     * 개발 모드 설정
     *
     * @param devMode true일 경우 실행된 SQL 로그를 콘솔에 출력합니다.
     */
    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    /**
     * DB 연결 생성
     *
     * @return Connection 객체
     * @throws SQLException DB 연결 실패 시 예외 발생
     */
    public Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":3306/" + dbName + "?serverTimezone=Asia/Seoul";
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * 단순 SQL 실행 (DDL, DML)
     *
     * 👉 INSERT, UPDATE, DELETE 같은 쿼리를 직접 실행할 때 사용합니다.
     *
     * @param sql 실행할 SQL 쿼리
     * @param params PreparedStatement 파라미터
     */
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

    /**
     * Sql 객체 생성
     *
     * 👉 체이닝 방식으로 SQL을 작성하고 실행할 수 있는 빌더 객체를 반환합니다.
     *
     * @return Sql 빌더 객체
     */
    public Sql genSql() {
        return new Sql(this);
    }
}
