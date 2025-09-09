package com.back.article.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Sql {
    private final SimpleDb simpleDb;
    private final StringBuilder sqlBuilder = new StringBuilder();
    private final List<Object> params = new ArrayList<>();

    public Sql(SimpleDb simpleDb) {
        this.simpleDb = simpleDb;
    }

    public Sql append(String sql, Object... params) {
        if (!sqlBuilder.isEmpty()) {
            sqlBuilder.append(" ");
        }
        sqlBuilder.append(sql);

        if (params != null) {
            this.params.addAll(Arrays.asList(params));
        }

        return this;
    }

    public long insert() {
        try (Connection conn = simpleDb.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString(), Statement.RETURN_GENERATED_KEYS)) {

            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }

            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
