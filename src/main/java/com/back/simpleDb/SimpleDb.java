package com.back.simpleDb;

import lombok.Setter;

import java.sql.*;
import java.util.Arrays;
import java.util.stream.IntStream;

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

    public void run(String sql) {
        try (Statement stmt = conn.createStatement();){
            int rs = stmt.executeUpdate(sql);
            System.out.println("[sql] " + sql);
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
}

class Main {
    public static void main(String[] args) {
        //DB 연결 테스트
        SimpleDb simpleDb = new SimpleDb("localhost", "root", "1403", "simpleDb__test");

        //정적 sql 테스트
        simpleDb.run("DROP TABLE IF EXISTS article");
        simpleDb.run("""
                CREATE TABLE article (
                    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
                    PRIMARY KEY(id),
                    createdDate DATETIME NOT NULL,
                    modifiedDate DATETIME NOT NULL,
                    title VARCHAR(100) NOT NULL,
                    `body` TEXT NOT NULL,
                    isBlind BIT(1) NOT NULL DEFAULT 0
                )
                """);

        //동적 sql 테스트
        IntStream.rangeClosed(1, 6).forEach(no -> {
            boolean isBlind = no > 3;
            String title = "제목%d".formatted(no);
            String body = "내용%d".formatted(no);

            simpleDb.run("""
                    INSERT INTO article
                    SET createdDate = NOW(),
                    modifiedDate = NOW(),
                    title = ?,
                    `body` = ?,
                    isBlind = ?
                    """, title, body, isBlind);
        });
    }
}
