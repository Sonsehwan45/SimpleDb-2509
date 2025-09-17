package com.back.simpleDb;


import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
public class SimpleDb {
    private final String url;
    private final String user;
    private final String password;

    @Setter
    private boolean devMode = false;


    private final ThreadLocal<Connection> txConn = new ThreadLocal<>();

    public SimpleDb(String host, String user, String password, String dbName) {
        this.url = "jdbc:mysql://" + host + ":3306/" + dbName
                + "?serverTimezone=Asia/Seoul&sessionVariables=time_zone='%2B09:00'";
        this.user = user;
        this.password = password;
    }


    public Connection connect() throws SQLException {
        Connection tx = txConn.get();
        if (tx != null) {
            return uncloseable(tx);
        }
        return DriverManager.getConnection(url, user, password);
    }

    public void run(String sql, Object... params) {
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            if (devMode) {
                log.debug("개발 SQL 실행: {} | params={}", sql, params.length);
            }
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }


    public void close() {
        Connection c = txConn.get();
        if (c != null) {
            try { c.close(); } catch (SQLException ignored) {}
            finally { txConn.remove(); }
            if (devMode) log.debug("CLOSE");
        }
    }

    public void startTransaction() {
        if (txConn.get() != null) {
            throw new IllegalStateException("이미 트랜잭션이 시작되었습니다.");
        }
        try {
            Connection c = DriverManager.getConnection(url, user, password);
            c.setAutoCommit(false);
            txConn.set(c);
            if (devMode) log.debug("START");
        } catch (SQLException e) {
            throw new RuntimeException("트랜잭션 시작 실패: " + e.getMessage(), e);
        }
    }


    public void commit() {
        Connection c = txConn.get();
        if (c == null) throw new IllegalStateException("활성 트랜잭션이 없습니다.");
        try {
            c.commit();
        } catch (SQLException e) {
            throw new RuntimeException("커밋 실패: " + e.getMessage(), e);
        } finally {
            try { c.close(); } catch (SQLException ignored) {}
            txConn.remove();
            if (devMode) log.debug("COMMIT");
        }
    }


    public void rollback() {
        Connection c = txConn.get();
        if (c == null) throw new IllegalStateException("활성 트랜잭션이 없습니다.");
        try {
            c.rollback();
        } catch (SQLException e) {
            throw new RuntimeException("롤백 실패: " + e.getMessage(), e);
        } finally {
            try { c.close(); } catch (SQLException ignored) {}
            txConn.remove();
            if (devMode) log.debug("ROLLBACK");
        }
    }


    private Connection uncloseable(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        if (devMode) log.trace("close()");
                        return null;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException ite) {
                        throw ite.getTargetException();
                    }
                }
        );
    }
}