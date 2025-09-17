package com.back.db;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class EntityMapper {

    private final Map<Class<?>, Map<String, Method>> cache = new ConcurrentHashMap<>();

    <T> T mapRow(Class<T> clazz, ResultSet rs) throws SQLException {
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            T instance = clazz.getDeclaredConstructor().newInstance();
            int columnCount = metaData.getColumnCount();

            // 변환된 적 없는 Entity는 cache 등록
            Map<String, Method> setters = cache.computeIfAbsent(clazz, this::computeSetters);

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Method setter = setters.get(columnName);
                setter.invoke(instance, rs.getObject(i));
            }
            return instance;
        } catch (NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | InstantiationException e) {
            log.error("리플렉션 예외 발생 {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private Map<String, Method> computeSetters(Class<?> clazz) {
        Map<String, Method> setters = new HashMap<>();
        try {
            for (Field field : clazz.getDeclaredFields()) {
                String fieldName = field.getName();

                String setterName = convertSetterName(fieldName, field.getType());
                Method setter = clazz.getMethod(setterName, field.getType());

                String columnName = convertColumnName(fieldName);
                setters.put(columnName, setter);
            }
        } catch (NoSuchMethodException e) {
            log.error("세터 메소드 찾기 실패: {}", clazz.getName(), e);
        }
        return setters;
    }

    private String convertColumnName(String fieldName) {
        return fieldName;
    }

    private String convertSetterName(String columnName, Class<?> fieldType) {
        if (fieldType.equals(boolean.class) && columnName.startsWith("is")) {
            return "set" + columnName.substring(2, 3).toUpperCase() + columnName.substring(3);
        }
        return "set" + columnName.substring(0, 1).toUpperCase() + columnName.substring(1);
    }
}
