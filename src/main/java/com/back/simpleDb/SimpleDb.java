package com.back.simpleDb;

import lombok.Setter;

import java.sql.*;
import java.util.*;

@Setter
public class SimpleDb {

    private boolean devMode;
    private Connection conn;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
        connectDb(url, user, passwd);
    }

    private void connectDb(String url, String user, String passwd) {
        try {
            this.conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void logSql(String sql, Object... args) {
        if(!devMode) { return; }
        System.out.println("========== SQL ==========");
        System.out.println("sql " + sql);
        System.out.println("args " + Arrays.toString(args));
    }

    private void logErr(SQLException e, String sql, Object... args) {
        if(!devMode) { return; }
        System.err.println("========== ERROR ==========");
        System.err.println("sql " + sql);
        System.err.println("args " + Arrays.toString(args));
        e.printStackTrace(System.err);
    }


    private void setArgs(PreparedStatement pstmt, Object... args) throws SQLException {
        for(int i=0; i<args.length; i++) {
            pstmt.setObject(i+1, args[i]);
        }
    }

    public void run(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            setArgs(pstmt, args);

            pstmt.executeUpdate();

            logSql(sql, args);
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }

    public long insert(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setArgs(pstmt, args);

            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();

            long newId = 0;
            if(rs.next()) {
                newId = rs.getLong(1);
            }

            logSql(sql, args);

            return newId;
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public int updateOrDelete(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            setArgs(pstmt, args);

            int affectedRows = pstmt.executeUpdate(); //영향 받은 행의 수

            logSql(sql, args);

            return affectedRows;
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> selectRows(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            setArgs(pstmt, args);

            try(ResultSet rs = pstmt.executeQuery()) {
                logSql(sql, args);

                List<Map<String, Object>> rows = new ArrayList<>();

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        String columnName = metaData.getColumnName(i);
                        Object value = rs.getObject(i);
                        row.put(columnName, value);
                    }
                    rows.add(row);
                }

                return rows;
            }
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> selectRow(String sql, Object... args) {
        List<Map<String,Object>> rows = selectRows(sql, args);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }
}
