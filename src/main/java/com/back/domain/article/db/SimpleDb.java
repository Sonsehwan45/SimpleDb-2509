package com.back.domain.article.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SimpleDb {
    // DB 접속 정보를 저장하는 필드 (package-private으로 변경하여 Sql 클래스에서 접근 가능하도록 수정)
    final String url;
    final String user;
    final String password;
    // 개발 모드 활성화 여부
    private boolean devMode;

    //각 스레드의 Connection을 독립적으로 저장하기 위한 ThreadLocal
    private final ThreadLocal<Connection> connectionThreadLocal;

    // 생성자: DB 접속 정보를 받아 필드를 초기화합니다.
    public SimpleDb(String host, String user, String password, String dbName) {
        this.url = "jdbc:mysql://" + host + "/" + dbName + "?useUnicode=true&characterEncoding=utf8&autoReconnect=true&serverTimezone=Asia/Seoul";
        this.user = user;
        this.password = password;
        this.devMode = false;
        this.connectionThreadLocal = new ThreadLocal<>();
    }

    // 개발자 모드를 설정하는 메서드
    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    // 개발 모드인지 확인하는 내부용(package-private) 메서드
    boolean isDevMode() {
        return this.devMode;
    }

    Connection getConnection() {
        //ThreadLocal에서 현재 스레드에 저장된 Connection을 가져옴
        Connection conn = connectionThreadLocal.get();
        try{
            if (conn == null || conn.isClosed()){
                // DriverManager를 통해 새로운 DB 연결을 생성
                conn = DriverManager.getConnection(url, user, password);
                connectionThreadLocal.set(conn);
            }
        } catch (SQLException e) {
            // DB 연결 중 오류 발생 시, 런타임 예외로 전환하여 알림
            throw new RuntimeException(e);
        }
        // 준비된 Connection 객체를 반환
        return conn;
    }

    // [스레드 관리 해결] 현재 스레드에 할당된 Connection을 닫고 ThreadLocal에서 제거
    public void close() {
        // ThreadLocal에서 현재 스레드의 Connection을 가져옴
        Connection conn = connectionThreadLocal.get();
        // Connection이 존재하면
        if (conn != null) {
            try {
                // Connection이 닫혀있지 않으면 실제로 닫음
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ignored) {
                // close 과정에서 발생하는 예외는 일반적으로 무시함
            } finally {
                // 매우 중요: 메모리 누수 방지를 위해 ThreadLocal에서 반드시 제거
                connectionThreadLocal.remove();
            }
        }
    }

    //결과값이 필요 없는 단순 SQL(CREATE, INSERT, TRUNCATE 등)을 실행하는 편의 메서드입니다.
    public void run(String sql, Object... params) {
        // 내부적으로 Sql 객체를 생성하고, append와 update를 연속으로 호출하여 SQL을 실행합니다.
        new Sql(this).append(sql, params).update();
    }

    public Sql genSql() {
        return new Sql(this);
    }
}
