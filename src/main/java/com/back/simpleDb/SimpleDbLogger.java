package com.back.simpleDb;

import lombok.Setter;

import java.util.Arrays;

/**
 * SimpleDbLogger 클래스
 * -----------------------------
 * SimpleDb에서 발생하는 SQL 실행, 트랜잭션, DB 연결, 예외를 로그로 출력하는 유틸리티 클래스
 *
 * 주요 기능:
 * 1. SQL 로그
 *    - sql(String sql, Object... args) : SQL 문자열과 바인딩 파라미터 출력
 * 2. 오류 로그
 *    - error(String message, Exception e) : 일반 예외 출력
 *    - error(String message, Exception e, String sql, Object... args) : SQL 실행 시 예외와 쿼리, 파라미터 출력
 * 3. 트랜잭션 로그
 *    - tx(String action) : 트랜잭션 시작, 커밋, 롤백 알림
 * 4. DB 연결 로그
 *    - db(String message) : DB 연결 성공/실패 알림
 *
 * devMode가 false이면 로그 출력하지 않음
 */
@Setter
public class SimpleDbLogger {

    /** 개발 모드 여부, false면 로그 출력 안함 */
    private boolean devMode;

    /** 생성자 */
    SimpleDbLogger(boolean devMode) {
        this.devMode = devMode;
    }

    /** 현재 스레드명 반환 */
    private String thread() {
        return "[" + Thread.currentThread().getName() + "]";
    }

    /** SQL 실행 로그 출력 */
    public void sql(String sql, Object... args) {
        if(!devMode) return;
        sql = sql.trim();
        System.out.println(thread() + "[SQL] " + sql);
        System.out.println(thread() + "[ARGS] " + Arrays.toString(args));
    }

    /** 일반 예외 로그 출력 */
    public void error(String message, Exception e) {
        if(!devMode) return;
        System.err.println(thread() + "[ERROR] " + message);
        e.printStackTrace(System.err);
    }

    /** SQL 실행 시 예외 로그 출력 */
    public void error(String message, Exception e, String sql, Object... args) {
        if(!devMode) return;
        sql = sql.trim();
        System.err.println(thread() + "[ERROR] " + message);
        System.err.println(thread() + "[SQL] " + sql);
        System.err.println(thread() + "[ARGS] " + Arrays.toString(args));
        e.printStackTrace(System.err);
    }

    /** 트랜잭션 로그 출력 (시작, 커밋, 롤백 등) */
    public void tx(String action) {
        if(!devMode) return;
        System.out.println(thread() + "[TX] " + action);
    }

    /** DB 연결 상태 로그 출력 */
    public void db(String message) {
        if(!devMode) return;
        System.out.println(thread() + "[DB] " + message);
    }
}

