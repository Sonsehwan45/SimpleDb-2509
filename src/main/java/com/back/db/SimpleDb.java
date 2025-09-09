package com.back.db;


public class SimpleDb {

    private final String host;
    private final String userName;
    private final String password;
    private final String database;

    public SimpleDb(String host, String userName, String password, String database) {
        this.host = host;
        this.userName = userName;
        this.password = password;
        this.database = database;
    }


    public void run(String sql) {

    }

    public void run(String sql, Object... args) {

    }

    public void setDevMode(boolean b) {

    }


    public Sql genSql() {
        return null;
    }

    public void close() {

    }

    public void startTransaction() {

    }

    public void rollback() {
    }

    public void commit() {

    }
}
