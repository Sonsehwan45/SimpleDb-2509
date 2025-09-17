package com.back.db;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class Sql {

    private final StringBuilder builder;
    private final List<Object> bindingArgs;
    private final SimpleDb simpleDb;

    Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
        builder = new StringBuilder();
        bindingArgs = new ArrayList<>();
    }

    public Sql append(String sql, Object... args) {
        builder.append(sql).append(" ");
        bindingArgs.addAll(Arrays.asList(args));
        return this;
    }

    // 가변인자에 아무 것도 전달되지 않아도 String sql만 전달 된다면 append는 컴파일 에러 발생하지않고 잘 작동함
    // 하지만, 이 메소드가 따로 선언 되는 것이 더 나은 사용자 경험이라고 생각하여 추가하였음
    // 이는 SimpleDb.run()도 마찬가지임
    public Sql append(String sql) {
        builder.append(sql).append(" ");
        return this;
    }

    public Sql appendIn(String sql, Object... args) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int i = 0; i < args.length; i++) {
            joiner.add("?");
        }
        String replace = sql.replace("?", joiner.toString());
        builder.append(replace).append(" ");

        bindingArgs.addAll(Arrays.asList(args));

        return this;
    }

    public long insert() {
        return simpleDb.runInsert(builder.toString(), bindingArgs.toArray());
    }

    public int update() {
        return simpleDb.runUpdate(builder.toString(), bindingArgs.toArray());
    }

    public int delete() {
        return simpleDb.runDelete(builder.toString(), bindingArgs.toArray());
    }

    public Map<String, Object> selectRow() {
        return simpleDb.queryRowToMap(builder.toString(), bindingArgs.toArray());
    }

    public <T> T selectRow(Class<T> clazz) {
        return simpleDb.queryRow(builder.toString(), bindingArgs.toArray(), clazz);
    }

    public List<Map<String, Object>> selectRows() {
        return simpleDb.queryRowsToMaps(builder.toString(), bindingArgs.toArray());
    }

    public <T> List<T> selectRows(Class<T> clazz) {
        return simpleDb.queryRows(builder.toString(), bindingArgs.toArray(), clazz);
    }

    public LocalDateTime selectDatetime() {
        return simpleDb.queryColumn(builder.toString(), bindingArgs.toArray());
    }

    public Long selectLong() {
        return simpleDb.queryColumn(builder.toString(), bindingArgs.toArray());
    }

    public String selectString() {
        return simpleDb.queryColumn(builder.toString(), bindingArgs.toArray());
    }

    public List<Long> selectLongs() {
        return simpleDb.queryColumns(builder.toString(), bindingArgs.toArray());
    }

    public Boolean selectBoolean() {
        return simpleDb.queryBooleanColumn(builder.toString(), bindingArgs.toArray());
    }
}