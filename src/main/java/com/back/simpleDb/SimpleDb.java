package com.back.simpleDb;

import lombok.Setter;

import java.sql.*;
import java.util.*;

@Setter
public class SimpleDb {

    private boolean devMode;
    Connection conn;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        try {
            String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
            this.conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
     }

    public void run(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            for(int i=0; i<args.length; i++) {
                pstmt.setObject(i+1, args[i]);
            }

            int rs = pstmt.executeUpdate();

            System.out.println("[sql] " + sql);
            System.out.println("[args]" + Arrays.toString(args));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }

    public long insert(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            for(int i=0; i<args.length; i++) {
                pstmt.setObject(i+1, args[i]);
            }

            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();

            long newId = 0;
            if(rs.next()) {
                newId = rs.getLong(1);
            }

            System.out.println("[sql] " + sql);
            System.out.println("[args]" + Arrays.toString(args));

            return newId;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int updateOrDelete(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            for(int i=0; i<args.length; i++) {
                pstmt.setObject(i+1, args[i]);
            }

            int affectedRows = pstmt.executeUpdate(); //영향 받은 행의 수

            System.out.println("[sql] " + sql);
            System.out.println("[args]" + Arrays.toString(args));

            return affectedRows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Map<String, Object>> select(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            for(int i=0; i<args.length; i++) {
                pstmt.setObject(i+1, args[i]);
            }

            ResultSet rs = pstmt.executeQuery();

            System.out.println("[sql] " + sql);
            System.out.println("[args]" + Arrays.toString(args));

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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
