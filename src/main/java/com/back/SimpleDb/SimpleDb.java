package com.back.SimpleDb;

import com.back.Article;

import java.lang.reflect.InvocationTargetException;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class SimpleDb {
	private final String dbHost;
	private final String dbUser;
	private final String dbPassword;
	private final String dbName;
	private boolean devMode;

	private final ThreadLocal<Connection> connection;

	public SimpleDb(String host, String user, String password, String dbName) { // 생성자 적용
		this.dbHost = host;
		this.dbUser = user;
		this.dbPassword = password;
		this.dbName = dbName;
		this.devMode = false;
		this.connection = new ThreadLocal<>();
	}

	public void setDevMode(boolean devMode) { //devmode 설정
		this.devMode = devMode;
	}

	private Connection getThreadLocalConnection() { // 커넥션 관리
		Connection conn = connection.get();
		if (conn == null) {
			String url = String.format("jdbc:mysql://%s/%s?useUnicode=true&characterEncoding=utf8", dbHost, dbName);
			try {
				conn = DriverManager.getConnection(url, dbUser, dbPassword);
				connection.set(conn);
			} catch (SQLException e) {
				System.out.println("DB 연결 실패");
				throw new SimpleDbException("데이터베이스 연결에 실패했습니다.", e);
			}
		}
		return conn;
	}

	private PreparedStatement prepareStatement(String query, Object... params) {
		if (devMode) {
			System.out.println("== Raw SQL ==");
			System.out.println(query);
			if (params != null && params.length > 0) {
				System.out.println("== Bindings ==");
				for (int i = 0; i < params.length; i++) {
					System.out.println((i + 1) + ": " + params[i]);
				}
			}
		}
		try {
			Connection conn = getThreadLocalConnection();
			PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);

			if (params != null) {
				for (int i = 0; i < params.length; i++) {
					ps.setObject(i + 1, params[i]);
				}
			}
			return ps;
		} catch (SQLException e) {
			throw new SimpleDbException("PreparedStatement 생성에 실패했습니다. Query: " + query, e);
		}
	}

	public void run(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new SimpleDbException("쿼리 실행에 실패했습니다. Query: " + query, e);
		}
	}

	public void run(String query) {
		run(query, (Object[]) null);
	}


	public Sql genSql() {
		return new Sql();
	}

	public void close() {
		Connection conn = connection.get();
		try {
			if (conn != null && !conn.isClosed()) {
				conn.close();
			}
		} catch (SQLException e) {
			throw new SimpleDbException("데이터베이스 연결을 닫는 데 실패했습니다.", e);
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
			throw new SimpleDbException("Insert 쿼리 실행에 실패했습니다. Query: " + query, e);
		}
	}

	private int runUpdate(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new SimpleDbException("Update 쿼리 실행에 실패했습니다. Query: " + query, e);
		}
	}

	private int runDelete(String query, Object... params) {
		try (PreparedStatement ps = prepareStatement(query, params)) {
			return ps.executeUpdate();
		} catch (SQLException e) {
			throw new SimpleDbException("Delete 쿼리 실행에 실패했습니다. Query: " + query, e);
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
			throw new SimpleDbException("Select(rows) 쿼리 실행에 실패했습니다. Query: " + query, e);
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
			throw new SimpleDbException("Select(row) 쿼리 실행에 실패했습니다. Query: " + query, e);
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
			throw new SimpleDbException("Select(single value) 쿼리 실행에 실패했습니다. Query: " + query, e);
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
			throw new SimpleDbException("Select(longs) 쿼리 실행에 실패했습니다. Query: " + query, e);
		}
		return longs;
	}

	public void startTransaction() {
		try {
			getThreadLocalConnection().setAutoCommit(false);
		} catch (SQLException e) {
			throw new SimpleDbException("트랜잭션 시작에 실패했습니다.", e);
		}
	}

	public void rollback() {
		Connection conn = connection.get();
		try {
			if (conn != null) {
				conn.rollback();
			}
		} catch (SQLException e) {
			throw new SimpleDbException("트랜잭션 롤백에 실패했습니다.", e);
		} finally {
			try {
				if (conn != null) conn.setAutoCommit(true);
			} catch (SQLException ignored) {}
		}
	}

	public void commit() {
		Connection conn = connection.get();
		try {
			if (conn != null) {
				conn.commit();
				conn.setAutoCommit(true);
			}
		} catch (SQLException e) {
			throw new SimpleDbException("트랜잭션 커밋에 실패했습니다.", e);
		}
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
				values = Collections.emptyList(); // IN 절에 빈 리스트가 들어올 경우
			} else if (params.length == 1 && params[0] instanceof Collection) {
				values = (Collection<?>) params[0];
			} else {
				values = Arrays.asList(params);
			}

			if (values.isEmpty()) {
				String modifiedQuery = query.replaceFirst("\\?", "NULL");
				// 참고: `col IN (NULL)`은 `col = NULL`과 다르며 항상 false를 반환합니다.
				// 빈 리스트에 대해 `1 = 0` 과 같은 항상 false인 조건을 추가하는 것도 좋은 방법입니다.
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
			return SimpleDb.this.runInsert(sb.toString(), binding.toArray());
		}

		public int update() {
			return SimpleDb.this.runUpdate(sb.toString(), binding.toArray());
		}

		public int delete() {
			return SimpleDb.this.runDelete(sb.toString(), binding.toArray());
		}

		public List<Map<String, Object>> selectRows() {
			return SimpleDb.this.runSelectRows(sb.toString(), binding.toArray());
		}

		public Map<String, Object> selectRow() {
			return SimpleDb.this.runSelectRow(sb.toString(), binding.toArray());
		}

		public <T> List<T> selectRows(Class<T> clazz) {
			List<Map<String, Object>> rows = selectRows();
			List<T> objects = new ArrayList<>();
			for (Map<String, Object> row : rows) {
				objects.add(mapToObject(row, clazz));
			}
			return objects;
		}

		public <T> T selectRow(Class<T> clazz) {
			Map<String, Object> row = selectRow();
			if (row.isEmpty()) {
				return null;
			}
			return mapToObject(row, clazz);
		}

		private <T> T mapToObject(Map<String, Object> map, Class<T> clazz) {
			try {
				T instance = clazz.getDeclaredConstructor().newInstance();
				for (Map.Entry<String, Object> entry : map.entrySet()) {
					String columnName = entry.getKey();
					Object value = entry.getValue();
					try {
						// DB의 snake_case 컬럼명을 Java의 camelCase 필드명으로 변환 (예: member_id -> memberId)
						String fieldName = toCamelCase(columnName);
						java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
						field.setAccessible(true);
						field.set(instance, value);
					} catch (NoSuchFieldException | IllegalAccessException ignored) {
						// 매핑되는 필드가 없으면 무시
					}
				}
				return instance;
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				throw new SimpleDbException(clazz.getName() + " 객체 생성 또는 필드 설정에 실패했습니다.", e);
			}
		}

		private String toCamelCase(String s) {
			String[] parts = s.split("_");
			StringBuilder camelCaseString = new StringBuilder(parts[0]);
			for (int i = 1; i < parts.length; i++) {
				camelCaseString.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase());
			}
			return camelCaseString.toString();
		}

		public LocalDateTime selectDatetime() {
			return SimpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public Long selectLong() {
			return SimpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public String selectString() {
			return SimpleDb.this.queryResult(sb.toString(), binding.toArray());
		}

		public Boolean selectBoolean() {
			return SimpleDb.this.queryBooleanResult(sb.toString(), binding.toArray());
		}

		public List<Long> selectLongs() {
			return SimpleDb.this.queryLongsResult(sb.toString(), binding.toArray());
		}

	}
}
