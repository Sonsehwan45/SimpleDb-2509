package com.back.simpleDb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Sql 클래스
 * -----------------------------
 * SimpleDb를 이용한 SQL 빌더 및 실행 클래스
 *
 * 주요 기능:
 * 1. SQL 빌드
 *    - append(), appendIn()으로 SQL 문자열과 파라미터 동적 구성
 * 2. DML 실행
 *    - insert(), update(), delete() 등
 * 3. SELECT 조회
 *    - selectRows(), selectRow()로 Map 또는 DTO 반환
 * 4. 특정 컬럼 조회
 *    - selectColumnSingle(Class<T> type): 단일 행 단일 컬럼
 *    - selectColumnList(Class<T> type): 단일 컬럼 여러 행
 *    - 편의 메서드: selectLongs(), selectLong(), selectString(), selectBoolean(), selectDatetime()
 * 5. DTO 변환 지원
 *    - Jackson ObjectMapper 사용, JavaTimeModule 등록
 */

public class Sql {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final SimpleDb db;
    StringBuffer querySb = new StringBuffer();
    List<Object> args = new ArrayList<>();

    // -------------------------------
    // 생성자
    // -------------------------------

    Sql(SimpleDb db) {
        this.db = db;
    }

    // -------------------------------
    // SQL 빌더 메서드
    // -------------------------------

    /** SQL 문자열과 파라미터를 추가 */
    public Sql append(String sql, Object... args) {

        //바인딩 파라미터 누적
        if(args.length > 0) {
            this.args.addAll(Arrays.asList(args));
        }

        //쿼리 문자열 누적
        querySb.append(sql).append(" ");

        //체이닝 지원
        return this;
    }

     /** IN절 지원 SQL 문자열과 파라미터 추가 */
    public Sql appendIn(String sql, Object... args) {
        if(args.length > 0) {
            this.args.addAll(Arrays.asList(args));

            //하나의 '?'를 args 수에 맞게 확장
            String placeholder = IntStream.range(0, args.length)
                    .mapToObj(i -> "?")
                    .collect(Collectors.joining(", "));
            sql = sql.replace("?", placeholder);
        }
        querySb.append(sql).append(" ");
        return this;
    }

    // -------------------------------
    // DML 실행
    // -------------------------------

    /** INSERT 실행 후 AUTO_INCREMENT 키 반환 */
    public long insert() {
        return db.insert(querySb.toString(), args.toArray());
    }

    /** UPDATE 실행 후 영향 받은 row 수 반환 */
    public int update() {
        return db.update(querySb.toString(), args.toArray());
    }

    /** DELETE 실행 후 영향 받은 row 수 반환 */
    public int delete() {
        return db.delete(querySb.toString(), args.toArray());
    }

    // -------------------------------
    // SELECT
    // -------------------------------

    /** 복수 행 조회, Map 리스트 반환 */
    public List<Map<String, Object>> selectRows() {
        return db.select(querySb.toString(), args.toArray());
    }

    /** 복수 행 조회 후 DTO 리스트로 변환 */
    public <T> List<T> selectRows(Class<T> type) {
        List<Map<String, Object>> rows = selectRows();

        List<T> dtoList = new ArrayList<>();

        //MAP -> DTO 변환
        for(Map<String, Object> row : rows) {
            T dto = mapper.convertValue(row, type);
            dtoList.add(dto);
        }
        return dtoList;
    }

    /** 단일 행 조회, Map 반환 */
    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = db.select(querySb.toString(), args.toArray());

        //결과가 있으면 첫번쨰 값 반환
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 단일 행 조회 후 DTO 변환 */
    public <T> T selectRow(Class<T> type) {
        Map<String, Object> row = selectRow();

        //결과가 있으면 MAP -> DTO로 변환 후 반환
        if(row == null || row.isEmpty()) { return  null; }
        return mapper.convertValue(row, type);
    }

    // -------------------------------
    // 단일 컬럼 조회
    // -------------------------------

    /** 단일 컬럼 여러 행 조회 */
    public <T> List<T> selectColumnList(Class<T> type) {
        List<Map<String, Object>> rows = selectRows();

        //결과가 없다면 빈 List 반환
        if(rows.isEmpty()) return Collections.emptyList();

        // Map -> 지정 타입 변환 후 리스트 누적
        List<T> columnList = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            //첫번째 컬럼값만 추출
            Object value = row.values().iterator().next();

            //해당 타입일 경우 list에 추가
            if(type.isInstance(value)) {
                columnList.add(type.cast(value));
            }
            //해당 타입이 아닐 경우 null 추가
            else columnList.add(null);
        }
        return columnList;
    }

    /** 단일 컬럼 단일 행 조회 */
    private <T> T selectColumnSingle(Class<T> type) {
        Map<String, Object> row = selectRow();

        //결과값이 없다면 명시적으로 null 반환
        if(row == null || row.isEmpty()) return null;

        //첫번째 값만 가져오기
        Object value = row.values().iterator().next();

        //Boolean 타입일 때 예외처리 (Long -> Boolean)
        if(type == Boolean.class) {
            if(value instanceof Long) {
                return (T) Boolean.valueOf(((Long) value).longValue() != 0);
            }
        }

        //해당 type일 경우 반환
        if(type.isInstance(value)) {
            return (T) value;
        }

        //해당 타입이 아닐 경우 null 반환
        return null;
    }

    // -------------------------------
    // 편의 메서드
    // -------------------------------

    /** 단일 컬럼 여러 행 조회, Long 타입 편의 메서드 */
    public List<Long> selectLongs() {
        return selectColumnList(Long.class);
    }

    /** 단일 컬럼 단일 값 조회, LocalDateTime 타입 편의 메서드 */
    public LocalDateTime selectDatetime() {
        return selectColumnSingle(LocalDateTime.class);
    }

    /** 단일 컬럼 단일 값 조회, Long 타입 편의 메서드 */
    public Long selectLong() {
        return selectColumnSingle(Long.class);
    }

    /** 단일 컬럼 단일 값 조회, String 타입 편의 메서드 */
    public String selectString() {
        return selectColumnSingle(String.class);
    }

    /** 단일 컬럼 단일 값 조회, Boolean 타입 편의 메서드 */
    public Boolean selectBoolean() {
        return selectColumnSingle(Boolean.class);
    }
}
