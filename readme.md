# SimpleDb & Sql 클래스 구조 정리

직접 만든 JDBC 간단 래퍼(Wrapper) 구조를 설명합니다.

Spring Data JPA 같은 ORM 없이, 직접 SQL을 날리되 편리하게 쓰기 위한 도우미 클래스입니다.

---

## 1. SimpleDb (DB 연결 전담)

SimpleDb는 **DB 연결을 담당하는 도우미 클래스**입니다.

```java
public class SimpleDb {     
    private final String host;     
    private final String user;     
    private final String password;     
    private final String dbName; 
}
```

### 역할

- DB 접속 정보(`host`, `user`, `password`, `dbName`)를 가지고 있음
- `Connection` 객체를 열어줌

### 핵심 메서드

```java
public Connection getConnection() throws SQLException {     
    String url = "jdbc:mysql://" 
	    + host + ":3306/" 
	    + dbName + "?serverTimezone=Asia/Seoul";     
    return DriverManager.getConnection(url, user, password); 
}
```

👉 실제 MySQL 서버와 연결을 맺고 `Connection` 객체를 반환합니다.

이걸 **DB 문 열쇠**라고 생각하면 됩니다.

---

## 2. Sql (SQL 작성 & 실행기)

Sql은 **SQL을 조립하고 실행하는 도구**입니다.

SimpleDb로 연결을 열고, Sql로 문장을 만들어 실행합니다.

```java
private final StringBuilder sqlBuilder = new StringBuilder(); 
private final List<Object> params = new ArrayList<>();
```

### 주요 메서드

- `append(String sql, Object... params)`

  👉 SQL 조각과 파라미터를 추가합니다. 체이닝 가능.

- `insert()`

  👉 INSERT 실행, 생성된 키(ID)를 반환.

- `update()`

  👉 UPDATE 실행, 수정된 row 개수 반환.

- `delete()`

  👉 DELETE 실행, 삭제된 row 개수 반환.

- `selectRows()`

  👉 SELECT 실행, 결과를 `List<Map<String,Object>>` 형태로 반환.

- `selectRow()`

  👉 SELECT 실행, 결과 중 첫 번째 row만 반환.

- `selectDatetime()`

  👉 SELECT 실행 후 결과를 `LocalDateTime`으로 반환합니다.

  👉 주로 `NOW()` 같은 SQL 함수 결과를 받을 때 사용하며, null이면 예외 발생.

- `selectLong()`

  👉 SELECT 실행 후 결과를 `Long` 타입으로 반환합니다.

- `selectString()`

  👉 SELECT 실행 후 결과를 `String` 타입으로 반환합니다.

- `selectBoolean()`

  👉 SELECT 실행 후 결과를 `Boolean` 타입으로 반환합니다.

  👉 1/0, TRUE/FALSE, 조건식 결과를 Boolean으로 변환


---

## 3. 전체 동작 흐름

1. `SimpleDb`가 DB 연결을 담당한다.
    - `Connection`을 얻는 책임을 가짐
2. `Sql`이 실제 SQL 문장을 조립하고 실행한다.
    - SQL 조각을 `append()`로 이어 붙임
    - 실행 시 `Connection`은 `SimpleDb`에서 빌려옴
    - 실행 후에는 항상 `close()`로 정리 (finally 블록)

---

## 4. 예시 사용법

```java
SimpleDb simpleDb = new SimpleDb("localhost", "root", "1234", "mydb");

// INSERT 예시
long id = simpleDb.genSql()
    .append("INSERT INTO article (title, body) VALUES (?, ?)", "제목", "내용")
    .insert();

// SELECT 예시
Map<String, Object> row = simpleDb.genSql()
    .append("SELECT * FROM article WHERE id = ?", id)
    .selectRow();
```