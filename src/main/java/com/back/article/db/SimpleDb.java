package com.back.article.db;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;

/**
 * DB 연결 및 SQL 실행을 담당하는 클래스
 *
 * 👉 DB 연결(Connection) 생성, 단순 SQL 실행, Sql 빌더 객체 제공 기능을 지원합니다.
 *
 * 멀티스레드 환경(WebMVC 등)에서 각 스레드가 독립적인 Connection을 사용하도록 ThreadLocal 적용
 */
@Slf4j
public class SimpleDb {
    private final String host;
    private final String user;
    private final String password;
    private final String dbName;
    private boolean devMode = false;

    // 각 스레드별로 독립적인 Connection을 저장
    private final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();

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
     * 멀티스레드 환경 안전하게 DB Connection 반환
     *
     * - 각 스레드는 독립적인 Connection 객체를 사용
     * - 이미 열려 있는 경우 기존 Connection 반환
     * - 스레드 종료 시 simpleDb.closeThreadConnection() 호출로 정리 필요
     *
     * @return 현재 스레드 전용 Connection 객체
     * @throws SQLException DB 연결 실패 시 예외 발생
     */
    public Connection getConnection() throws SQLException {
        Connection conn = threadConnection.get();
        if (conn == null || conn.isClosed()) {
            String url = "jdbc:mysql://" + host + ":3306/" + dbName + "?serverTimezone=Asia/Seoul";
            conn = DriverManager.getConnection(url, user, password);
            threadConnection.set(conn);
        }
        return conn;
    }

    /**
     * 현재 스레드의 Connection 종료 및 ThreadLocal 제거
     *
     * - 반드시 스레드 종료 전에 호출해야 메모리 누수 방지
     */
    public void closeThreadConnection() {
        Connection conn = threadConnection.get();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ignored) {}
            threadConnection.remove();
        }
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
        PreparedStatement pstmt = null;
        try {
            Connection conn = getConnection(); // ThreadLocal 전용 Connection 사용
            pstmt = conn.prepareStatement(sql);

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            pstmt.executeUpdate();

            if (devMode) {
//                System.out.println("[DEBUG] SQL 실행: " + sql);
                log.debug("SQL 실행: {}", sql);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            // PreparedStatement만 닫고 Connection은 ThreadLocal에서 관리
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
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

    /**
     * SimpleDb 자원 정리
     *
     * 👉 애플리케이션 종료 시 호출하여 현재 스레드의 Connection을 닫습니다.
     */
    public void close() {
        closeThreadConnection();
    }


    /**
     * 트랜잭션 시작
     *
     * 👉 자동 커밋 모드를 비활성화하여 트랜잭션을 시작합니다.
     */
    public void startTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 트랜잭션 롤백
     *
     * 👉 오류 발생 시 트랜잭션을 롤백하고 자동 커밋 모드로 복원합니다.
     */
    public void rollback() {
        try {
            Connection conn = getConnection();
            conn.rollback();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 트랜잭션 커밋
     *
     * 👉 모든 작업이 성공적으로 완료되었을 때 트랜잭션을 커밋하고 자동 커밋 모드로 복원합니다.
     */
    public void commit() {
        try {
            Connection conn = getConnection();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
