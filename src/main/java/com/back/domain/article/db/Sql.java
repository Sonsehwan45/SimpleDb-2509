package com.back.domain.article.db;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sql {
    private final SimpleDb simpleDb;
    private final StringBuilder rawSql;
    private final List<Object> params;

    Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
        this.rawSql = new StringBuilder();
        this.params = new ArrayList<>();
    }

    // SQL 문장과 파라미터를 추가하는 메서드
    public Sql append(String sql, Object... params) {
        rawSql.append(sql).append(" ");
        //받은 파라미터들을 List에 추가
        for (Object param : params) {
            this.params.add(param);
        }
        return this;
    }

    //전송된 SQL 쿼리 확인을 위한 메서드
    private void logSql() {
        if(simpleDb.isDevMode()){
            System.out.println("== rawSql ==");
            System.out.println(rawSql.toString().trim());
            if(!params.isEmpty()){
                System.out.println("== params ==");
                for(Object param : params){
                    System.out.println(param);
                }
            }
        }
    }

    //
    private void bindParams(PreparedStatement pstmt) throws SQLException {
        for(int i = 0; i < params.size(); i++){
            pstmt.setObject(i + 1, params.get(i));
        }
    }

    /**
     * INSERT 쿼리를 실행하고, 자동 생성된 ID(Primary Key)를 반환합니다.
     * @return 생성된 ID. 실패 시 -1 반환
     */
    public long insert() {
        logSql(); // SQL 로그 출력
        // try-with-resources: conn과 pstmt를 자동으로 close
        try (Connection conn = DriverManager.getConnection(simpleDb.url, simpleDb.user, simpleDb.password);
             // RETURN_GENERATED_KEYS 옵션으로 PreparedStatement 생성
             PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim(), Statement.RETURN_GENERATED_KEYS)) {

            bindParams(pstmt); // 파라미터 바인딩

            // INSERT 쿼리 실행
            pstmt.executeUpdate();

            // 생성된 키(ID) 값을 가져옴
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                // 결과가 있다면
                if (rs.next()) {
                    // 첫 번째 컬럼의 ID 값을 long으로 읽어서 반환
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            // 오류 발생 시 런타임 예외로 전환
            throw new RuntimeException(e);
        }

        // ID 생성 실패 시 -1 반환
        return -1;
    }

    /**
     * INSERT, UPDATE, DELETE 등 데이터 변경 쿼리를 실행하고
     * 영향받은 row의 개수를 반환합니다.
     */
    public int update() {
        logSql();
        // try-with-resources 구문으로 Connection과 PreparedStatement 자원을 자동 해제
        try (Connection conn = DriverManager.getConnection(simpleDb.url, simpleDb.user, simpleDb.password);
             PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim())) {

            bindParams(pstmt);

            // SQL을 실행하고, 영향받은 row의 개수를 반환
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            // 예외 발생 시 RuntimeException으로 전환하여 처리 중단
            throw new RuntimeException(e);
        }
    }

    public int delete() {
        return update();
    }

    public List<Map<String, Object>> selectRows() {
        logSql();

        List<Map<String, Object>> rows = new ArrayList<>();

        // Connection, PreparedStatement, ResultSet 모두 try-with-resources로 자원 관리
        try (Connection conn = DriverManager.getConnection(simpleDb.url, simpleDb.user, simpleDb.password);
             PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim());
             ResultSet rs = pstmt.executeQuery()) {

            bindParams(pstmt);

            // ResultSet의 메타데이터(컬럼 정보)를 루프 시작 전에 한 번만 가져옴
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // while 루프를 돌며 각 row를 처리
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();

                // for 루프를 돌며 현재 row의 모든 컬럼을 Map에 담음
                for (int i = 1; i <= columnCount; i++) {
                    // 컬럼명을 가져옴 (AS 별칭이 있을 경우를 대비해 getColumnLabel 사용)
                    String columnName = metaData.getColumnLabel(i);
                    // 컬럼의 데이터 값을 가져옴
                    Object value = rs.getObject(i);

                    // t004 테스트 통과를 위한 타입 변환
                    if (value instanceof Timestamp) {
                        // DB의 DATETIME/TIMESTAMP 타입을 Java의 LocalDateTime으로 변환
                        value = ((Timestamp) value).toLocalDateTime();
                    } else if (value instanceof Long && metaData.getColumnTypeName(i).equals("BIT")) {
                        // DB의 BIT(1) 타입을 Java의 boolean으로 변환
                        value = (long) value == 1;
                    }

                    row.put(columnName, value);
                }
                rows.add(row);
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return rows;
    }

    //혹시 결과가 한개가 아니여도 첫번째 row만 반환
    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = selectRows();

        if (rows.isEmpty()) {
            return null;
        }
        //첫번째 row만 반환
        return rows.getFirst();
    }

    public LocalDateTime selectDatetime() {
        Map<String, Object> row = selectRow();

        if (row.isEmpty()) {
            return null;
        }
        //Map에 들어있는 첫 번째 값(Value)을 꺼내서 LocalDateTime으로 형변환 후 반환
        return (LocalDateTime) row.values().iterator().next();
    }
}
