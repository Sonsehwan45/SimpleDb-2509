package com.back.article.db;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Sql - 간단한 SQL 빌더 및 실행 헬퍼
 *
 * 이 클래스는 PreparedStatement를 사용해 SQL을 안전하게 실행하고,
 * 실행 결과를 편리하게 추출하기 위한 간단한 유틸입니다.
 *
 * 주요 특징 및 주의사항:
 *  - 아직 스레드에 사용하기 부적합하지 않음 (Connection은 SimpleDb ThreadLocal에서 관리)
 *  - SQL 조각을 append할 때 외부 입력을 직접 문자열로 붙이지 말고
 *    항상 물음표(?) 플레이스홀더와 파라미터 바인딩을 사용하세요.
 */
public class Sql {
    // DB 연결을 얻어오는 역할(풀을 사용하도록 구현되어 있어야 함)
    private final SimpleDb simpleDb;

    // SQL을 조합하기 위한 StringBuilder
    private final StringBuilder sqlBuilder = new StringBuilder();

    // PreparedStatement에 바인딩할 파라미터 목록
    private final List<Object> params = new ArrayList<>();

    public Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    /**
     * SQL 조각과 파라미터를 추가한다.
     *
     * 사용 예:
     *   new Sql(db).append("SELECT * FROM post WHERE id = ?", id).selectRow();
     *
     * 주의:
     *  - sql 문자열에 외부 값을 직접 붙이지 말고 반드시 "?" 를 사용하고
     *    파라미터로 값을 전달하세요. 그렇지 않으면 SQL 인젝션 위험이 존재합니다.
     *
     * @param sql  SQL 문자열(부분 쿼리 가능). 여러 번 append로 이어붙일 수 있음.
     * @param args 물음표 플레이스홀더(?)에 바인딩할 값들
     * @return this (메서드 체이닝 지원)
     */
    public Sql append(String sql, Object... args) {
        if (sqlBuilder.length() > 0) {
            sqlBuilder.append(" "); // 이전에 추가한 SQL과 구분을 위해 공백 추가
        }
        sqlBuilder.append(sql);
        if (args != null) {
            this.params.addAll(Arrays.asList(args));
        }
        return this;
    }

    /**
     * INSERT 실행 후 자동 생성된 키(Generated Key)를 반환한다.
     *
     * 반환 값:
     *  - 생성된 키가 있으면 해당 키(첫 번째)를 long으로 반환
     *  - 없으면 0을 반환
     *
     * 예외 처리:
     *  - SQLException 발생 시 RuntimeException으로 래핑하여 던진다.
     *
     * 리소스 정리:
     *  - ResultSet, PreparedStatement만 닫고 Connection은 ThreadLocal에서 관리
     */
    public long insert() {
        Connection conn = null;          // DB 서버와 연결
        PreparedStatement pstmt = null;  // SQL 준비 및 파라미터 바인딩
        ResultSet rs = null;             // 실행 결과(생성된 키 등) 조회

        try {
            conn = simpleDb.getConnection(); // 스레드 전용 Connection 사용
            pstmt = conn.prepareStatement(sqlBuilder.toString(), Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            pstmt.executeUpdate();

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            // close 순서: ResultSet -> PreparedStatement
            // Connection은 ThreadLocal에서 관리하므로 닫지 않음
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * UPDATE 실행 (또는 기타 executeUpdate 용 SQL)
     */
    public int update() {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = simpleDb.getConnection();
            pstmt = conn.prepareStatement(sqlBuilder.toString());

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * DELETE 실행
     */
    public int delete() {
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = simpleDb.getConnection();
            pstmt = conn.prepareStatement(sqlBuilder.toString());

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * 여러 행 조회
     */
    public List<Map<String, Object>> selectRows() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = simpleDb.getConnection();
            pstmt = conn.prepareStatement(sqlBuilder.toString());

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            rs = pstmt.executeQuery();

            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
        }
    }

    /**
     * 단일 행 조회
     */
    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();
        if (rows.isEmpty()) return null;
        return rows.get(0);
    }

    /**
     * 단일 컬럼을 LocalDateTime으로 반환
     */
    public LocalDateTime selectDatetime() {
        Map<String, Object> row = selectRow();
        if (row == null || row.isEmpty()) return LocalDateTime.now();

        Object value = row.values().iterator().next();
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        } else if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        } else if (value != null) {
            return LocalDateTime.parse(value.toString());
        } else {
            return LocalDateTime.now();
        }
    }

    /**
     * 단일 컬럼을 Long으로 반환
     */
    public Long selectLong() {
        Map<String, Object> row = selectRow();
        if (row == null || row.isEmpty()) return null;

        Object value = row.values().iterator().next();
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }

    /**
     * 단일 컬럼을 String으로 반환
     */
    public String selectString() {
        Map<String, Object> row = selectRow();
        if (row == null || row.isEmpty()) return null;

        Object value = row.values().iterator().next();
        return value != null ? value.toString() : null;
    }

    /**
     * 단일 컬럼을 Boolean으로 반환
     */
    public Boolean selectBoolean() {
        Map<String, Object> row = selectRow();
        if (row == null || row.isEmpty()) return null;

        Object value = row.values().iterator().next();
        if (value instanceof Boolean) return (Boolean) value;
        else if (value instanceof Number) return ((Number) value).intValue() != 0;
        else if (value instanceof String) return Boolean.parseBoolean((String) value);
        else return null;
    }
}
