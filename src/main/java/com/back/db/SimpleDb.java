package com.back.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Setter;

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

    // throws SQLException을 명시하기 위한 Function<PreparedStatement, T> 함수형 인터페이스
    @FunctionalInterface
    interface StatementCallback<T> {
        T apply(PreparedStatement statement) throws SQLException;
    }

    private <T> T runTemplate(String sql, Object[] args, StatementCallback<T> callback) {
        System.out.println(sql);
        System.out.print("====\n");

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // 바인딩할 값이 없을 경우 생략
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    statement.setObject(i + 1, args[i]);
                }
            }

            return callback.apply(statement);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 바인딩할 값이 없는 경우
    private <T> T runTemplate(String sql, StatementCallback<T> callback) {
        return runTemplate(sql, null, callback);
    }

    public void run(String sql, Object... args) {
        System.out.println(sql);
        runTemplate(sql, args, statement -> statement.executeUpdate());
    }

    public void run(String sql) {
        runTemplate(sql, statement -> statement.executeUpdate());
    }

    private long runInsert(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            statement.executeUpdate();
            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                return 0L;
            }
        });
    }

    private int runUpdate(String sql, Object... args) {
        return runTemplate(sql, args, statement -> statement.executeUpdate());
    }

    private int runDelete(String sql, Object... args) {
        return runTemplate(sql, args, statement -> statement.executeUpdate());
    }

    private Map<String, Object> convertRowToMap(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return queryRowToMap(rs);
            }
            return new HashMap<>();
        });
    }

    private Map<String, Object> queryRowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            row.put(metaData.getColumnName(i), rs.getObject(i));
        }
        return row;
    }

    private List<Map<String, Object>> queryRowsToMaps(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            ResultSet rs = statement.executeQuery();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(queryRowToMap(rs));
            }
            return rows;
        });
    }

    private <T> T queryColumn(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return (T) rs.getObject(1);
            }
            return null;
        });
    }

    public void close() {

    }

    public void startTransaction() {

    }

    public void rollback() {
    }

    public void commit() {

    }

    public Sql genSql() {
        return new Sql();
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
            return SimpleDb.this.runInsert(builder.toString(), bindingArgs.toArray());
        }

        public int update() {
            return SimpleDb.this.runUpdate(builder.toString(), bindingArgs.toArray());
        }

        public int delete() {
            return SimpleDb.this.runDelete(builder.toString(), bindingArgs.toArray());
        }

        public Map<String, Object> selectRow() {
            return SimpleDb.this.convertRowToMap(builder.toString(), bindingArgs.toArray());
        }

        public List<Map<String, Object>> selectRows() {
            return SimpleDb.this.queryRowsToMaps(builder.toString(), bindingArgs.toArray());
        }

        public <T> List<T> selectRows(Class<T> clazz) {
            return null;
        }

        public <T> T selectRow(Class<T> clazz) {
            return null;
        }

        public LocalDateTime selectDatetime() {
            return SimpleDb.this.queryColumn(builder.toString(), bindingArgs.toArray());
        }

        public Long selectLong() {
            return SimpleDb.this.queryColumn(builder.toString(), bindingArgs.toArray());
        }

        public String selectString() {
            return SimpleDb.this.queryColumn(builder.toString(), bindingArgs.toArray());
        }

        public Boolean selectBoolean() {
            return SimpleDb.this.queryColumn(builder.toString(), bindingArgs.toArray());
        }

        public List<Long> selectLongs() {
            return SimpleDb.this.queryColumn(builder.toString(), bindingArgs.toArray());
        }
    }
}
