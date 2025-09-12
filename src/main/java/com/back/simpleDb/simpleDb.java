package com.back.simpleDb;

import com.back.Article;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class simpleDb {
	private static String dbhost;
	private static String dbuser;
	private static String dbpassword;
	private static String dbname;
	private boolean devmode;

	private static ThreadLocal<Connection> connection; // ThreadLocal 적용 대비

	public simpleDb(String host, String user, String password, String dbName) { // 생성자 적용
		dbhost = host;
		dbuser = user;
		dbpassword = password;
		dbname = dbName;
		devmode = false;

		connection = new ThreadLocal<Connection>();
		Sql sql;
		sql = genSql();
	}

	public void setDevMode(boolean devMode) {
		devmode = devMode;
	}

	private PreparedStatement prepareStatement(String query, Object... params) throws SQLException {
		Connection conn = connection.get();
		PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

		if (params != null) {
			for (int i = 0; i < params.length; i++) {
				ps.setObject(i + 1, params[i]);
			}
		}
		return ps;
	}

	public void run(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	public void run(String query) {
		run(query, (Object[]) null);
	}


	public Sql genSql() { // 쓰레드마다 connection 생성???
		try {
			String url = "jdbc:mysql://" + dbhost + "/" + dbname;
			Connection conn = DriverManager.getConnection(url, dbuser, dbpassword);
			connection.set(conn);
		} catch (Exception e) {
			System.out.println("DB 연결 실패");
		}

		return new Sql();
	}

	public void close() {
		Connection c = connection.get();
		try {
			if (c != null) {
				c.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		} finally {
			connection.remove();
		}
	}

	private long runInsert(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			ps.executeUpdate();
			try (ResultSet rs = ps.getGeneratedKeys()) {
				if (rs.next()) {
					return rs.getLong(1);
				}
				return 0L;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private int runUpdate(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private int runDelete(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private List<Map<String, Object>> runSelectRows(String query, Object... params) {
		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = prepareStatement(query, params);
			 ResultSet resultSet = ps.executeQuery()) {
			ResultSetMetaData metaData = resultSet.getMetaData();
			int columnCount = metaData.getColumnCount();
			while (resultSet.next()) {
				Map<String, Object> row = new HashMap<>();
				for (int i = 1; i <= columnCount; i++) {
					row.put(metaData.getColumnName(i), resultSet.getObject(i));
				}
				rows.add(row);
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return rows;
	}

	private Map<String, Object> runSelectRow(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params);
			 ResultSet resultSet = ps.executeQuery()) {
			if (resultSet.next()) {
				Map<String, Object> row = new HashMap<>();
				ResultSetMetaData metaData = resultSet.getMetaData();
				int columnCount = metaData.getColumnCount();
				for (int i = 1; i <= columnCount; i++) {
					row.put(metaData.getColumnName(i), resultSet.getObject(i));
				}
				return row;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return Collections.emptyMap();
	}

	private <T> T queryResult(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params);
			 ResultSet resultSet = ps.executeQuery()) {
			if (resultSet.next()) {
				return (T) resultSet.getObject(1);
			}
			return null;
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	private Boolean queryBooleanResult(String query, Object... params) {
		Object result = queryResult(query, params);
		if (result instanceof Boolean) {
			return (Boolean) result;
		} else if (result instanceof Number) {
			return ((Number) result).intValue() == 1;
		}
		return null;
	}

	private List<Long> queryLongsResult(String query, Object... params) {
		List<Long> longs = new ArrayList<>();
		try (PreparedStatement ps = prepareStatement(query, params);
			 ResultSet resultSet = ps.executeQuery()) {
			while (resultSet.next()) {
				longs.add(resultSet.getLong(1));
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
		return longs;
	}

	public void startTransaction() {

	}

	public void rollback() {

	}

	public void commit() {

	}

	public class Sql {
		private StringBuilder sb;
		private List<Object> binding;

		public Sql() {
			binding = new ArrayList<>();
			sb = new StringBuilder();
		}

		public Sql append(String query, Object... params) {
			sb.append(query).append(" ");
			binding.addAll(Arrays.asList(params));
			return this;
		}

		public Sql appendIn(String query, Object... params) {
			Collection<?> values;
			if (params == null || params.length == 0) {
				values = Collections.emptyList();
	} else if (params.length == 1 && params[0] instanceof Collection) {
				values = (Collection<?>) params[0];
			} else {
				values = Arrays.asList(params);
			}

			if (values.isEmpty()) {
				String modifiedQuery = query.replaceFirst("\\?", "NULL");
				sb.append(modifiedQuery).append(" ");
				return this;
			}

			String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
			String modifiedQuery = query.replaceFirst("\\?", placeholders);
			sb.append(modifiedQuery).append(" ");
			binding.addAll(values);

			return this;
		}

		public long insert() {
			return simpleDb.this.runInsert(sb.toString(), binding.toArray());
		}

		public int update() {
			return simpleDb.this.runUpdate(sb.toString(), binding.toArray());
		}

		public int delete() {
			return simpleDb.this.runDelete(sb.toString(), binding.toArray());
		}

		public List<Map<String, Object>> selectRows() {
			return simpleDb.this.runSelectRows(sb.toString(), binding.toArray());
		}

		public Map<String, Object> selectRow() {
			return simpleDb.this.runSelectRow(sb.toString(), binding.toArray());
		}

		public List<Article> selectRows(Class<Article> clazz) {
			return null;
		}

		public Article selectRow(Class<Article> clazz) {
			return null;
		}

		public LocalDateTime selectDatetime() {
			return simpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public Long selectLong() {
			return simpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public String selectString() {
			return simpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public Boolean selectBoolean() {
			return simpleDb.this.queryBooleanResult(sb.toString(), binding.toArray());
		}

		public List<Long> selectLongs() {
			return simpleDb.this.queryLongsResult(sb.toString(), binding.toArray());
		}

	}
}
