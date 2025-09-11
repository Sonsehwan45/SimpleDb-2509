package com.back.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Setter;
import org.springframework.stereotype.Component;

public class SimpleDb {

    private final String host;
    private final String userName;
    private final String password;
    private final String database;
    @Setter
    private boolean devMode;

    private static final int PORT = 3306;

    public SimpleDb(String host, String userName, String password, String database) {
        this.host = host;
        this.userName = userName;
        this.password = password;
        this.database = database;
    }

    // TODO : 커넥션 풀을 구현하여 커넥션 획득
    private Connection getConnection() throws SQLException {
        String URL = "jdbc:mysql://" + host + ":" + PORT + "/" + database;
        return DriverManager.getConnection(URL, userName, password);
    }

    public void run(String sql) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void run(String sql, Object... args) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private long runInsert(String sql, Object... args) {
        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.executeUpdate();

            // 생성된 키(ID) 가져오기
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    public Sql genSql() {
        return new Sql();
    }

    public void close() {

    }

    public void startTransaction() {

    }

    public void rollback() {
    }

    public void commit() {

    }

    public class Sql {

        private final StringBuilder builder;
        private final List<Object> bindingArgs;

        public Sql() {
            builder = new StringBuilder();
            bindingArgs = new ArrayList<>();
        }

        public Sql append(String sql, Object... args) {
            builder.append(sql).append(" ");
            bindingArgs.addAll(Arrays.asList(args));
            return this;
        }

        public Sql appendIn(String sql, Object... args) {
            return null;
        }

        public long insert() {
            System.out.println("Generated SQL: " + this.builder);
            return SimpleDb.this.runInsert(builder.toString(), bindingArgs.toArray());
        }

        public int update() {
            return 0;
        }

        public int delete() {
            return 0;
        }

        public List<Map<String, Object>> selectRows() {
            return null;
        }

        public <T> List<T> selectRows(Class<T> clazz) {
            return null;
        }

        public <T> T selectRow(Class<T> clazz) {
            return null;
        }

        public Map<String, Object> selectRow() {
            return null;
        }

        public LocalDateTime selectDatetime() {
            return null;
        }

        public Long selectLong() {
            return null;
        }

        public String selectString() {
            return null;
        }

        public Boolean selectBoolean() {
            return null;
        }

        public List<Long> selectLongs() {
            return null;
        }
    }

}
