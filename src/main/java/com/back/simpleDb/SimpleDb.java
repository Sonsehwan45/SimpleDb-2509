package com.back.simpleDb;

import lombok.Setter;

import java.sql.*;
import java.util.*;

@Setter
public class SimpleDb {

    private final String url;
    private final String user;
    private final String passwd;
    private boolean devMode;
    private final ThreadLocal<Connection> threadLocalConn = new ThreadLocal<>();

    public SimpleDb(String host, String user, String passwd, String dbName) {
        String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
        this.url = url;
        this.user = user;
        this.passwd = passwd;
    }

    private Connection connectDb(String url, String user, String passwd) {
        try {
            Connection conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
            return conn;
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Connection getConnection() {
        Connection conn = threadLocalConn.get();
        try {
            if(conn == null || conn.isClosed()) {
                conn = connectDb(url, user, passwd);
                threadLocalConn.set(conn);

                if(devMode) System.out.println("[ " + Thread.currentThread().getName() + " ]");
            }
        } catch(SQLException e) {
            throw new RuntimeException(e);
        }
        return conn;
    }

    public void close() {
        Connection conn = threadLocalConn.get();
        if(conn != null) {
            try {
                conn.close();
            } catch(SQLException e) {
                throw new RuntimeException(e);
            } finally {
                threadLocalConn.remove();
            }
        }
    }

    public void startTransaction() {
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            if (devMode) {
                System.out.println("[" + Thread.currentThread().getName() + "] 트랜잭션 시작");
            }
        } catch(SQLException e) {
            throw new RuntimeException("트랜잭션 시작 실패", e);
        }
    }

    public void commit() {
        try {
            Connection conn = getConnection();
            conn.commit();
            conn.setAutoCommit(true);
            if (devMode) {
                System.out.println("[" + Thread.currentThread().getName() + "] 트랜잭션 커밋");
            }
        } catch(Exception e) {
            throw new RuntimeException("커밋 실패", e);
        }
    }

    public void rollback() {
        try {
            Connection conn = getConnection();
            conn.rollback();
            conn.setAutoCommit(true);
            if (devMode) {
                System.out.println("[" + Thread.currentThread().getName() + "] 트랜잭션 롤백");
            }
        } catch(Exception e) {
            throw new RuntimeException("롤백 실패", e);
        }
    }

    private void logSql(String sql, Object... args) {
        if(!devMode) { return; }
        System.out.println("========== SQL ==========");
        System.out.println("sql " + sql);
        System.out.println("args " + Arrays.toString(args));
    }

    private void logErr(SQLException e, String sql, Object... args) {
        if(!devMode) { return; }
        System.err.println("========== ERROR ==========");
        System.err.println("sql " + sql);
        System.err.println("args " + Arrays.toString(args));
        e.printStackTrace(System.err);
    }

    private void setArgs(PreparedStatement pstmt, Object... args) throws SQLException {
        for(int i=0; i<args.length; i++) {
            pstmt.setObject(i+1, args[i]);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }

    @FunctionalInterface
    private interface SqlExecutor<T> {
        T execute(PreparedStatement pstmt) throws SQLException;
    }

    private <T> T run(String sql, SqlExecutor<T> executor, Object... args) {
        try(PreparedStatement pstmt = getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setArgs(pstmt, args);

            T result = executor.execute(pstmt);

            logSql(sql, args);

            return result;
        } catch(SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public void run(String sql, Object... args) {
        run(sql, PreparedStatement::executeUpdate, args);
    }

    public long insert(String sql, Object... args) {
        return run(sql, pstmt -> {
            pstmt.executeUpdate();
            try(ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                throw new IllegalStateException("생성된 키를 가져올 수 없습니다.");
            }
        }, args);
    }

    public int update(String sql, Object... args) {
        return run(sql, PreparedStatement::executeUpdate, args);
    }

    public int delete(String sql, Object... args) {
        return run(sql, PreparedStatement::executeUpdate, args);
    }

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
