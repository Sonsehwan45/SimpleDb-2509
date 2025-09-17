package com.back.simpleDb;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class Sql {
    private final SimpleDb simpleDb;
    private final StringBuilder sb = new StringBuilder();
    private final List<Object> params = new ArrayList<>();


    public Sql(SimpleDb  simpleDb) {
        this.simpleDb = simpleDb;
    }

    public Sql append(String sqlPart, Object... values) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(sqlPart);
        if (values != null) {
            params.addAll(Arrays.asList(values));
        }
        return this;
    }

    public Sql appendIn(String sqlPart, Object... values) {
        if (sb.length() > 0) sb.append(" ");
        int n = (values == null) ? 0 : values.length;

        String placeholders = (n == 0)? "NULL" : String.join(",", java.util.Collections.nCopies(n, "?"));

        String expanded = sqlPart.contains("(?)")
                ? sqlPart.replace("(?)", "(" + placeholders + ")") // WHERE id IN (?)
                : sqlPart.replace("?", placeholders);              // ORDER BY FIELD(id, ?)

        sb.append(expanded);
        if (n > 0) java.util.Collections.addAll(params, values);
        return this;
    }

    public long insert() {

        try(Connection conn = simpleDb.connect();
            PreparedStatement pstmt = conn.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS))  {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            pstmt.executeUpdate();
            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int update() {
        try(Connection conn = simpleDb.connect();
            PreparedStatement pstmt = conn.prepareStatement(sb.toString()))  {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public int delete() {
        try(Connection conn = simpleDb.connect();
            PreparedStatement pstmt = conn.prepareStatement(sb.toString()))  {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> selectRow() {
        try(Connection conn = simpleDb.connect();
            PreparedStatement pstmt = conn.prepareStatement(sb.toString()))  {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();

                if (rs.next()) {
                    return rowToMap(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public List<Map<String, Object>> selectRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        try(Connection conn = simpleDb.connect();
            PreparedStatement pstmt = conn.prepareStatement(sb.toString()))  {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();

                while (rs.next()) {
                    rows.add(rowToMap(rs));
                }
                return rows;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> List<T> selectRows(Class<T> type) {
        List<Map<String, Object>> rows = selectRows();
        List<T> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                T obj = type.getDeclaredConstructor().newInstance();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String fieldName = entry.getKey();
                    Object value = entry.getValue();
                    try {
                        var field = type.getDeclaredField(fieldName);
                        field.setAccessible(true);
                        field.set(obj, value);
                    } catch (NoSuchFieldException ignored) {}
                }
                result.add(obj);
            } catch (Exception e) {
                throw new RuntimeException("Failed to map row to " + type.getName(), e);
            }
        }
        return result;
    }

    public <T> T selectRow(Class<T> type) {
        List<T> rows = selectRows(type);
        if (rows.isEmpty()) return null;
        return rows.get(0);
    }

    private Map<String, Object> rowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta =  rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    public LocalDateTime selectDatetime() {
        Map<String, Object> row = selectRow();
        if(row.isEmpty()) return null;
        return (LocalDateTime) row.values().iterator().next();
    }

    public Long selectLong() {
        Map<String, Object> row = selectRow();
        if(row.isEmpty()) return null;

        return (Long)row.values().iterator().next();
    }

    public List<Long> selectLongs() {
        List<Long> out = new ArrayList<>();
        final String sql = sb.toString();
        try (Connection conn = simpleDb.connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Object v = rs.getObject(1);
                    if (v instanceof Number) out.add(((Number) v).longValue());
                    else if (v instanceof String) {
                        try { out.add(Long.parseLong(((String) v).trim())); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String selectString() {
        Map<String, Object> row = selectRow();
        if(row.isEmpty()) return null;

        return (String)row.values().iterator().next();
    }

    public boolean selectBoolean() {
        Map<String, Object> row = selectRow();
        if(row.isEmpty()) return false;
        Object value = row.values().iterator().next();
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number)  return ((Number) value).intValue() != 0;
        return false;
    }
}