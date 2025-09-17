package com.back.simpleDb;

import java.sql.*;
import java.util.*;

/**
 * SimpleDb 클래스
 * -----------------------------
 * JDBC 기반 DB 연결 관리 및 SQL 실행 클래스
 *
 * 주요 기능:
 * 1. DB 연결 관리
 *    - createConnection(String url, String user, String passwd) : DB 커넥션 생성
 *    - getConnection() : ThreadLocal에서 Connection 반환, 없으면 생성
 *    - close() : Connection 종료 및 ThreadLocal 제거
 * 2. 트랜잭션 관리
 *    - startTransaction() : 트랜잭션 시작 (AutoCommit false)
 *    - commit() : 트랜잭션 커밋 후 AutoCommit true
 *    - rollback() : 트랜잭션 롤백 후 AutoCommit true
 * 3. SQL 실행
 *    - run(String sql, Object... args) : 일반 DML 실행
 *    - insert(String sql, Object... args) : INSERT 실행 후 생성 키 반환
 *    - update(String sql, Object... args) : UPDATE 실행 후 영향 행 수 반환
 *    - delete(String sql, Object... args) : DELETE 실행 후 영향 행 수 반환
 *    - select(String sql, Object... args) : SELECT 실행 후 List<Map<String,Object>> 반환
 * 4. SQL/트랜잭션/DB 로그
 *    - logger.sql(String sql, Object... args) : SQL 실행 로그
 *    - logger.error(String message, Exception e) : 일반 예외 로그
 *    - logger.error(String message, Exception e, String sql, Object... args) : SQL 실행 예외 로그
 *    - logger.tx(String action) : 트랜잭션 시작/커밋/롤백 로그
 *    - logger.db(String message) : DB 연결 성공/실패 로그
 *
 * devMode가 false이면 로그 출력하지 않음
 */

public class SimpleDb {

    private final String url;
    private final String user;
    private final String passwd;
    private final SimpleDbLogger logger = new SimpleDbLogger(false);
    private final ThreadLocal<Connection> threadLocalConn = new ThreadLocal<>();

    // -------------------------------
    // 생성자
    // -------------------------------

    /** DB 연결을 위한 인자 저장 */
    public SimpleDb(String host, String user, String passwd, String dbName) {
        this.url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
        this.user = user;
        this.passwd = passwd;
    }

    // -------------------------------
    // 설정
    // -------------------------------

    /** 개발 모드 설정 (로그 출력 여부) */
    public void setDevMode(Boolean devMode) {
        logger.setDevMode(devMode);
    }

    /** SQL 빌더 객체 생성 */
    public Sql genSql() {
        return new Sql(this);
    }

    // -------------------------------
    // DB 연결 관리
    // -------------------------------

    /** DB 연결 생성 */
    private Connection createConnection(String url, String user, String passwd) {
        try {
            Connection conn = DriverManager.getConnection(url, user, passwd);
            logger.db("DB 연결 성공");
            return conn;
        } catch(SQLException e) {
            logger.error("DB 연결 실패", e);
            throw new RuntimeException(e);
        }
    }

    /** 현재 스레드 DB 연결 조회 */
    private Connection getConnection() {
        Connection conn = threadLocalConn.get();
        try {
            if(conn == null || conn.isClosed()) {
                conn = createConnection(url, user, passwd);
                threadLocalConn.set(conn);
            }
        } catch(SQLException e) {
            logger.error("DB 커넥션 조회 실패", e);
            throw new RuntimeException(e);
        }
        return conn;
    }

    /** 현재 스레드 DB 연결 종료 */
    public void close() {
        Connection conn = threadLocalConn.get();
        if(conn != null) {
            try {
                conn.close();
                logger.db("DB 연결 종료");
            } catch(SQLException e) {
                logger.error("DB 연결 종료 실패", e);
                throw new RuntimeException(e);
            } finally {
                threadLocalConn.remove();
            }
        }
    }

    // -------------------------------
    // 트랜잭션 관리
    // -------------------------------

    /** 트랜잭션 시작 */
    public void startTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            logger.tx("트랜잭션 시작");
        } catch(SQLException e) {
            logger.error("트랜잭션 시작 실패", e);
            throw new RuntimeException(e);
        }
    }

    /** 트랜잭션 커밋 */
    public void commit() {
        try {
            Connection conn = getConnection();
            conn.commit();
            conn.setAutoCommit(true);
            logger.tx("트랜잭션 커밋");
        } catch(Exception e) {
            logger.error("트랜잭션 커밋 실패", e);
            throw new RuntimeException(e);
        }
    }

    /** 트랜잭션 롤백 */
    public void rollback() {
        try {
            Connection conn = getConnection();
            conn.rollback();
            conn.setAutoCommit(true);
            logger.tx("트랜잭션 롤백");
        } catch(Exception e) {
            logger.error("트랜잭션 롤백 실패", e);
            throw new RuntimeException(e);

        }
    }

    // -------------------------------
    // SQL 실행 유틸리티
    // -------------------------------

    /** PreparedStatement에 파라미터 바인딩 */
    private void setArgs(PreparedStatement pstmt, Object... args) throws SQLException {
        for(int i=0; i<args.length; i++) {
            pstmt.setObject(i+1, args[i]);
        }
    }

    /** PreparedStatement 실행 함수형 인터페이스 */
    @FunctionalInterface
    private interface SqlExecutor<T> {
        T execute(PreparedStatement pstmt) throws SQLException;
    }

    /** SQL 실행 공통 로직 */
    private <T> T run(String sql, SqlExecutor<T> executor, Object... args) {
        try(PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setArgs(pstmt, args);

            T result = executor.execute(pstmt);

            logger.sql(sql, args);

            return result;
        } catch(SQLException e) {
            logger.error("SQL 실행 실패", e, sql, args);
            throw new RuntimeException(e);
        }
    }

    // -------------------------------
    // DML 실행
    // -------------------------------

    /** 일반 SQL 실행 (UPDATE/DELETE 등) */
    public void run(String sql, Object... args) {
        run(sql, PreparedStatement::executeUpdate, args);
    }

    /** INSERT 실행 후 AUTO_INCREMENT 키 반환 */
    public long insert(String sql, Object... args) {
        return run(sql, pstmt -> {
            pstmt.executeUpdate();
            try(ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                throw new IllegalStateException("생성된 키를 가져올 수 없습니다.");
            }
        }, args);
    }

    /** UPDATE 실행 후 영향받은 행 수 반환 */
    public int update(String sql, Object... args) {
        return run(sql, PreparedStatement::executeUpdate, args);
    }

    /** DELETE 실행 후 영향받은 행 수 반환 */
    public int delete(String sql, Object... args) {
        return run(sql, PreparedStatement::executeUpdate, args);
    }

    /** SELECT 실행 후 결과 리스트(Map) 반환 */
    public List<Map<String, Object>> select(String sql, Object... args) {
        return run(sql, pstmt -> {
            try(ResultSet rs = pstmt.executeQuery()) {

                List<Map<String, Object>> rows = new ArrayList<>();

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while(rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for(int i=1; i<=columnCount; i++) {
                        row.put(metaData.getColumnName(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }, args);
    }
}
