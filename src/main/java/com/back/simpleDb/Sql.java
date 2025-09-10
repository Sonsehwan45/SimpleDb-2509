package com.back.simpleDb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Sql {

    private final SimpleDb db;
    StringBuffer querySb = new StringBuffer();
    List<Object> args = new ArrayList<Object>();

    public Sql(SimpleDb db) {
        this.db = db;
    }

    public Sql append(String sql, Object... args) {
        querySb.append(sql).append(" ");
        if(args != null && args.length > 0) {
            this.args.addAll(Arrays.asList(args));
        }
        return this;
    }

    public long insert() {
        return db.insert(querySb.toString(), args.toArray());
    }

    public int update() {
        return db.updateOrDelete(querySb.toString(), args.toArray());
    }

    public int delete() {
        return db.updateOrDelete(querySb.toString(), args.toArray());
    }

    public List<Map<String, Object>> selectRows() {
        return db.select(querySb.toString(), args.toArray());
    }

    public Map<String, Object> selectRow() {
        List<Map<String, Object>> rows = this.selectRows();
        if (rows.isEmpty()) {
            return null;
        }
        return rows.get(0); // 첫 번째 row만 반환
    }
}
