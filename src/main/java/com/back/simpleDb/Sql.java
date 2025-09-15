package com.back.simpleDb;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Sql {

    private final SimpleDb db;
    StringBuffer querySb = new StringBuffer();
    List<Object> args = new ArrayList<Object>();

    public Sql(SimpleDb db) {
        this.db = db;
    }

    public Sql append(String sql, Object... args) {
        if(args.length > 0) {
            this.args.addAll(Arrays.asList(args));
        }
        querySb.append(sql).append(" ");
        return this;
    }

    public Sql appendIn(String sql, Object... args) {
        if(args.length > 0) {
            this.args.addAll(Arrays.asList(args));
            String placeholder = IntStream.range(0, args.length)
                    .mapToObj(i -> "?")
                    .collect(Collectors.joining(", "));

            sql = sql.replace("?", placeholder);
        }
        querySb.append(sql).append(" ");
        return this;
    }

    public long insert() {
        return db.insert(querySb.toString(), args.toArray());
    }

    public int update() {
        return db.update(querySb.toString(), args.toArray());
    }

    public int delete() {
        return db.delete(querySb.toString(), args.toArray());
    }

    public List<Map<String, Object>> selectRows() {
        return db.select(querySb.toString(), args.toArray());
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = db.select(querySb.toString(), args.toArray());
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }

    private <T> T selectColumnSingle(Class<T> type) {
        Map<String, Object> row = selectRow();
        if(row == null || row.isEmpty()) return null;

        Object value = row.values().iterator().next();
        if(type == Boolean.class) {
            if(value instanceof Long) {
                return (T) Boolean.valueOf(((Long) value).longValue() != 0);
            }
        }

        if(type.isInstance(value)) {
            return (T) value;
        }

        return null;
    }

    public <T> List<T> selectColumnList(Class<T> type) {
        List<Map<String, Object>> rows = selectRows();
        if(rows == null || rows.isEmpty()) return null;

        List<T> columnList = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!row.isEmpty()) {
                Object value = row.values().iterator().next();
                columnList.add(type.cast(value));
            }
        }
        return columnList;
    }

    public List<Long> selectLongs() {
        return selectColumnList(Long.class);
    }

    public LocalDateTime selectDatetime() {
        return selectColumnSingle(LocalDateTime.class);
    }

    public Long selectLong() {
        return selectColumnSingle(Long.class);
    }

    public String selectString() {
        return selectColumnSingle(String.class);
    }

    public Boolean selectBoolean() {
        return selectColumnSingle(Boolean.class);
    }
}
