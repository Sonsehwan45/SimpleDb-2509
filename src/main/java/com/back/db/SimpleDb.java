package com.back.db;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

@Slf4j
public class SimpleDb {

    private final String host;
    private final String userName;
    private final String password;
    private final String database;
    private final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    private static final int PORT = 3306;

    public SimpleDb(String host, String userName, String password, String database) {
        this.host = host;
        this.userName = userName;
        this.password = password;
        this.database = database;
    }

    // TODO : 커넥션 풀을 구현하여 thread-safe 커넥션 획득
    private Connection getConnection() throws SQLException {
        String URL = "jdbc:mysql://" + host + ":" + PORT + "/" + database;
        Connection connection = connectionHolder.get();
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(URL, userName, password);
            connectionHolder.set(connection);
        }
        return connection;
    }

    // throws SQLException을 명시하기 위한 Function<PreparedStatement, T> 함수형 인터페이스
    // 바인딩 완료된 PreparedStatement 이용해, 쿼리를 실행하고 적절한 결과값을 반환하도록 구현한다
    @FunctionalInterface
    interface StatementCallback<T> {
        T apply(PreparedStatement statement) throws SQLException;
    }

    private <T> T runTemplate(String sql, Object[] args, StatementCallback<T> callback) {
        // devMode일 경우, trace 레벨도 출력
        log.info("Executing SQL: {}", sql.trim());
        log.trace("Args {}", Arrays.toString(args));
        log.info("=======================================");

        // try - with resources (JAVA 7 이상)
        Connection connection = null;
        try {
            connection = getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                // 바인딩할 값이 없을 경우 생략
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        statement.setObject(i + 1, args[i]);
                    }
                }

                return callback.apply(statement);
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            try {
                if (connection != null && connection.getAutoCommit()) {
                    connection.close();
                    connectionHolder.remove();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void startTransaction() {
        try {
            getConnection().setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void commit() {
        try {
            Connection connection = connectionHolder.get();
            connection.commit();
            close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rollback() {
        try {
            Connection connection = connectionHolder.get();
            connection.rollback();
            close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            Connection connection = connectionHolder.get();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            connectionHolder.remove();
        }
    }

    // 바인딩할 값이 없는 경우
    private <T> T runTemplate(String sql, SimpleDb.StatementCallback<T> callback) {
        return runTemplate(sql, null, callback);
    }

    public void run(String sql, Object... args) {
        runTemplate(sql, args, PreparedStatement::executeUpdate);
    }

    public void run(String sql) {
        runTemplate(sql, PreparedStatement::executeUpdate);
    }

    long runInsert(String sql, Object... args) {
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

    int runUpdate(String sql, Object... args) {
        return runTemplate(sql, args, PreparedStatement::executeUpdate);
    }

    int runDelete(String sql, Object... args) {
        return runTemplate(sql, args, PreparedStatement::executeUpdate);
    }

    Map<String, Object> queryRowToMap(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            ResultSet rs = statement.executeQuery();
            if (rs.next()) {
                return convertRowToMap(rs);
            }
            return new HashMap<>();
        });
    }

    private Map<String, Object> convertRowToMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            row.put(metaData.getColumnName(i), rs.getObject(i));
        }
        return row;
    }

    List<Map<String, Object>> queryRowsToMaps(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(convertRowToMap(rs));
                }
                return rows;
            }
        });
    }

    <T> T queryRow(String sql, Object[] args, Class<T> clazz) {
        List<T> rows = queryRows(sql, args, clazz);
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            log.error("쿼리 결과가 여러개: {}", rows);
            throw new RuntimeException("쿼리 결과가 여러개" + sql);
        }
        return rows.get(0);
    }

    <T> List<T> queryRows(String sql, Object[] args, Class<T> clazz) {
        return runTemplate(sql, args, statement -> {
            List<T> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                while (rs.next()) {
                    T instance = clazz.getDeclaredConstructor().newInstance();
                    for (int i = 1; i <= columnCount; i++) {
                        // TODO : Reflection 데이터 캐싱
                        bindArguments(clazz, rs, metaData, i, instance);
                    }
                    rows.add(instance);
                }
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                throw new RuntimeException(e);
            }
            return rows;
        });
    }

    private <T> void bindArguments(Class<T> clazz, ResultSet rs, ResultSetMetaData metaData, int i, T instance)
            throws SQLException, NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        String columnName = metaData.getColumnName(i);

        String fieldName = convertCamelCase(columnName);
        Field field = clazz.getDeclaredField(fieldName);

        String setterName = convertSetterName(fieldName, field.getType());
        Method setter = clazz.getMethod(setterName, field.getType());

        setter.invoke(instance, rs.getObject(i));
    }

    private String convertCamelCase(String columnName) {
        while (columnName.endsWith("_")) {
            columnName = columnName.substring(0, columnName.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
                continue;
            }
            if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String convertSetterName(String columnName, Class<?> fieldType) {
        if (fieldType.equals(boolean.class) && columnName.startsWith("is")) {
            return "set" + columnName.substring(2, 3).toUpperCase() + columnName.substring(3);
        }
        return "set" + columnName.substring(0, 1).toUpperCase() + columnName.substring(1);
    }

    <T> T queryColumn(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return (T) rs.getObject(1);
                }
                return null;
            }
        });
    }

    <T> List<T> queryColumns(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                List<T> columns = new ArrayList<>();
                while (rs.next()) {
                    columns.add((T) rs.getObject(1));
                }
                return columns;
            }
        });
    }

    Boolean queryBooleanColumn(String sql, Object... args) {
        return runTemplate(sql, args, statement -> {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
                return null;
            }
        });
    }

    // devMode true 설정시 log레벨 변경
    public void setDevMode(boolean devMode) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = loggerContext.getLogger(SimpleDb.class);
        if (devMode) {
            logger.setLevel(Level.TRACE);
        } else {
            // default
            logger.setLevel(Level.INFO);
        }
    }

    public Sql genSql() {
        return new Sql(this);
    }
}
