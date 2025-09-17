package com.back.domain.article.db;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

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

    public Sql appendIn(String sql, Object... params) {
        int count = params.length;

        if(count==0){
            return this;
        }
        String placehlders = String.join(", ", Collections.nCopies(count, "?"));

        //WHERE id IN (?)를 파라미터의 개수에 맞게 ?를 추가하게 변경
        String newSql = sql.replace("?",  placehlders);

        rawSql.append(newSql).append(" ");

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

        // simpleDb에서 현재 스레드의 Connection을 받아옴
        Connection conn = simpleDb.getConnection();

        // Connection은 자동으로 닫지 않고, PreparedStatement만 자동으로 닫도록 try-with-resources 사용
        try (PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim(), Statement.RETURN_GENERATED_KEYS)) {

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

        Connection conn = simpleDb.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim())) {

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

        Connection conn = simpleDb.getConnection();

        try (PreparedStatement pstmt = conn.prepareStatement(rawSql.toString().trim());) {

            bindParams(pstmt);

            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();

                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();

                    for (int i = 1; i <= columnCount; i++) {
                        // 컬럼명을 가져옴 (AS 별칭이 있을 경우를 대비해 getColumnLabel 사용)
                        String columnName = metaData.getColumnLabel(i);

                        Object value = rs.getObject(i);
                        // DB의 DATETIME/TIMESTAMP 타입을 Java의 LocalDateTime으로 변환
                        if (value instanceof Timestamp) {
                            value = ((Timestamp) value).toLocalDateTime();
                            // DB의 BIT(1) 타입을 Java의 boolean으로 변환
                        } else if (value instanceof Long && metaData.getColumnTypeName(i).equals("BIT")) {
                            value = (long) value == 1;
                        }
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rows;
    }

    public <T> List<T> selectRows(Class<T> cls) {
        List<Map<String, Object>> rows = selectRows();
        return rows.stream()
                .map(row -> simpleDb.getObjectMapper().convertValue(row, cls))
                .toList();
    }

    //혹시 결과가 한개가 아니여도 첫번째 row만 반환
    public Map<String, Object> selectRow() {
        //실행된 쿼리의 결과 가져오기
        List<Map<String, Object>> rows = selectRows();

        if (rows.isEmpty()) {
            return null;
        }
        //첫번째 row만 반환
        return rows.getFirst();
    }

    public <T> T selectRow(Class<T> cls) {
        // 1. 기존 selectRow()를 호출하여 결과를 Map으로 받습니다.
        Map<String, Object> row = selectRow();

        // 2. 결과가 없으면(null이면) 그대로 null을 반환합니다.
        if (row == null) {
            return null;
        }

        // 3. ObjectMapper를 사용하여 Map을 원하는 클래스(cls)의 객체로 변환하여 반환합니다.
        return simpleDb.getObjectMapper().convertValue(row, cls);
    }

    public LocalDateTime selectDatetime() {
        //실행된 쿼리의 결과 가져오기
        Map<String, Object> row = selectRow();

        if (row == null || row.isEmpty()) {
            return null;
        }
        //Map에 들어있는 첫 번째 값(Value)을 꺼내서 LocalDateTime으로 형변환 후 반환
        return (LocalDateTime) row.values().iterator().next();
    }

    public Long selectLong() {
        //실행된 쿼리의 결과 가져오기
        Map<String, Object> row = selectRow();

        List<Map<String, Object>> rows = new ArrayList<>();

        if (row.isEmpty()) {
            return null;
        }

        //Map에 들어있는 첫 번째 값(Value)을 꺼내서 Long으로 형변환 후 반환
        return (Long) row.values().iterator().next();
    }

    public String selectString() {
        //실행된 쿼리의 결과 가져오기
        Map<String, Object> row = selectRow();

        if (row.isEmpty()) {
            return null;
        }

        //Map에 들어있는 첫 번째 값(Value)을 꺼내서 String으로 형변환 후 반환
        return row.values().iterator().next().toString();
    }

    public Boolean selectBoolean() {
        //실행된 쿼리의 결과 가져오기
        Map<String, Object> row = selectRow();

        if (row.isEmpty()) {
            return null;
        }

        Object value = row.values().iterator().next();

        //이미 값이 Boolean 타입일 때 그대로 반환
        if(value instanceof Boolean){
            return (Boolean) value;
        }

        //이미 값이 Number 타입일 때 그대로 반환
        if (value instanceof Number) {
            return ((Number) value).longValue() == 1;
        }

        return null;
    }


    public List<Long> selectLongs() {
        List<Long> longList = new ArrayList<>();

        List<Map<String, Object>> rows = selectRows();
        for (Map<String, Object> row : rows) {
            Long id = (Long) row.values().iterator().next();
            longList.add(id);
        }
        return longList;
    }
}
