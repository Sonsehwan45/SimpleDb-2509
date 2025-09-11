package com.back.simpleDb;

import java.sql.*;
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

    public long insert() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try  {
            conn = simpleDb.connect();
            pstmt = conn.prepareStatement(sb.toString(), Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            pstmt.executeUpdate();

            rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong(1);
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public int update() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try  {
            conn = simpleDb.connect();
            pstmt = conn.prepareStatement(sb.toString());
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }
    public int delete() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        try  {
            conn = simpleDb.connect();
            pstmt = conn.prepareStatement(sb.toString());
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public Map<String, Object> selectRow() {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try  {
            conn = simpleDb.connect();
            pstmt = conn.prepareStatement(sb.toString());

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();

            if (rs.next()) {
                return rowToMap(rs, meta);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
        return null;
    }

    public List<Map<String, Object>> selectRows() {
        List<Map<String, Object>> list = new ArrayList<>();
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try  {
            conn = simpleDb.connect();
            pstmt = conn.prepareStatement(sb.toString());

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            rs = pstmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();

            while (rs.next()) {
                list.add(rowToMap(rs, meta));
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
            if (pstmt != null) try { pstmt.close(); } catch (SQLException ignored) {}
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private Map<String, Object> rowToMap(ResultSet rs, ResultSetMetaData meta) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            row.put(meta.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }
}