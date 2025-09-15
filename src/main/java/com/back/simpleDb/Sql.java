package com.back.simpleDb;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Sql {

    private final SimpleDb db;
    StringBuffer querySb = new StringBuffer();
    List<Object> args = new ArrayList<Object>();

    public Sql(SimpleDb db) {
        this.db = db;
    }

    public Sql append(String sql, Object... args) {
        querySb.append(sql).append(" ");
        if(args.length > 0) {
            this.args.addAll(Arrays.asList(args));
        }
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

    private <T> T selectColumn(Class<T> type) {
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

    public LocalDateTime selectDatetime() {
        return selectColumn(LocalDateTime.class);
    }

    public Long selectLong() {
        return selectColumn(Long.class);
    }

    public String selectString() {
        return selectColumn(String.class);
    }

    public Boolean selectBoolean() {
        return selectColumn(Boolean.class);
    }
}
