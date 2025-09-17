# SimpleDb & Sql 클래스 구조 정리 (멀티스레드 반영)

직접 만든 JDBC 간단 래퍼(Wrapper) 구조를 설명합니다.

Spring Data JPA 같은 ORM 없이, 직접 SQL을 날리되 편리하게 쓰기 위한 도우미 클래스입니다.

---

## 1. SimpleDb (DB 연결 전담)

SimpleDb는 **DB 연결을 담당하는 도우미 클래스**입니다.  
멀티스레드 환경(Spring WebMVC 등)에서도 안전하게 동작하도록 **ThreadLocal을 사용하여 스레드마다 독립적인 Connection을 유지**합니다.

```java
public class SimpleDb {     
    private final String host;     
    private final String user;     
    private final String password;     
    private final String dbName; 
    private final ThreadLocal<Connection> threadConnection = new ThreadLocal<>();
}
```

### 역할

- DB 접속 정보(`host`, `user`, `password`, `dbName`)를 가지고 있음
- 스레드마다 독립적인 `Connection` 객체를 제공

### 핵심 메서드

```java
public Connection getConnection() throws SQLException {
Connection conn = threadConnection.get();
if (conn == null || conn.isClosed()) {
String url = "jdbc:mysql://" + host + ":3306/" + dbName + "?serverTimezone=Asia/Seoul";
conn = DriverManager.getConnection(url, user, password);
threadConnection.set(conn);
}
return conn;
}

// 스레드 전용 Connection 종료
public void closeThreadConnection() {
Connection conn = threadConnection.get();
if (conn != null) {
try { conn.close(); } catch (SQLException ignored) {}
threadConnection.remove();
}
}
```

- `getConnection()`  
  👉 스레드 전용 Connection을 반환. 없거나 닫혀 있으면 새로 생성.
- `closeThreadConnection()`  
  👉 스레드 전용 Connection을 종료하고 ThreadLocal에서 제거.


### 트랜잭션 관련 메서드

- `startTransaction()`  
  👉 자동 커밋 모드를 해제하여 트랜잭션 시작.
- `commit()`  
  👉 트랜잭션을 커밋하고 자동 커밋 모드로 복원.
- `rollback()`  
  👉 트랜잭션을 롤백하고 자동 커밋 모드로 복원.
- `close()`  
  👉 애플리케이션 종료 시 현재 스레드의 Connection을 닫음.

---

## 2. Sql (SQL 작성 & 실행기)

Sql은 **SQL을 조립하고 실행하는 도구**입니다.

SimpleDb로 연결을 열고, Sql로 문장을 만들어 실행합니다.  
멀티스레드 환경에서는 **Connection을 닫지 않고, PreparedStatement와 ResultSet만 닫도록** 설계되어 있습니다.

```java
private final StringBuilder sqlBuilder = new StringBuilder(); 
private final List<Object> params = new ArrayList<>();
private final SimpleDb simpleDb;
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

- `selectLong()`  
  👉 SELECT 실행 후 결과를 `Long` 타입으로 반환합니다.

- `selectString()`  
  👉 SELECT 실행 후 결과를 `String` 타입으로 반환합니다.

- `selectBoolean()`  
  👉 SELECT 실행 후 결과를 `Boolean` 타입으로 반환합니다.

---

## 3. 전체 동작 흐름

1. `SimpleDb`가 DB 연결을 담당한다.
    - `Connection`을 얻는 책임을 가짐
    - ThreadLocal을 통해 스레드마다 독립적인 Connection 유지
2. `Sql`이 실제 SQL 문장을 조립하고 실행한다.
    - SQL 조각을 `append()`로 이어 붙임
    - 실행 시 `Connection`은 `SimpleDb`에서 빌려옴
    - PreparedStatement와 ResultSet만 닫고, Connection은 ThreadLocal에 남김

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

---

## 5. insert() 동작 과정 예시

### 예시 코드
```java
Sql sql = simpleDb.genSql()
.append("INSERT INTO post (subject, body) VALUES (?, ?)", "제목1", "내용1");

long newId = sql.insert();
```

### 실행 과정

1. **SQL 조립**  
   `append()` 호출 시 SQL 문자열과 파라미터가 내부에 저장됩니다.  
   ```java
   sqlBuilder = "INSERT INTO post (subject, body) VALUES (?, ?)"
   params = ["제목1", "내용1"]
   ```

2. **DB 연결 생성**  
   `simpleDb.getConnection()` 호출을 통해 스레드 전용 Connection 사용.

3. **PreparedStatement 준비**  
   `conn.prepareStatement(..., Statement.RETURN_GENERATED_KEYS)` 로 준비합니다.  
   실행될 SQL:
   ```sql
   INSERT INTO post (subject, body) VALUES (?, ?)
   ```

4. **파라미터 바인딩**
    - `params[0] = "제목1"` → 첫 번째 `?`
    - `params[1] = "내용1"` → 두 번째 `?`  
      최종 실행 SQL:
      ```sql
      INSERT INTO post (subject, body) VALUES ('제목1', '내용1');
      ```

5. **쿼리 실행**  
   `pstmt.executeUpdate()` 실행으로 DB에 새로운 레코드가 삽입됩니다.

6. **생성된 키 가져오기**  
   `pstmt.getGeneratedKeys()` 호출로 자동 증가된 PK(id)를 조회합니다.

7. **자원 해제**  
   `ResultSet`, `PreparedStatement`만 닫고, Connection은 ThreadLocal에 남김.

### PreparedStatement, Connection, ResultSet 역할
- Connection: DB 연결 생성 및 관리 (스레드 전용, ThreadLocal)
- PreparedStatement: SQL 실행 준비 및 파라미터 바인딩
- ResultSet: SELECT 결과 조회 (insert에서는 생성된 키 조회)

---

## 6. 트러블 슈팅
### 1) build.gradle.kts 관련 문제

자료에 주어진 코드를 그대로 쓰려 했으나 문제 발생

→ 주어진 코드에 spring 관련 의존성을 주입해 해결

```java
implementation("org.springframework.boot:spring-boot-starter")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

### 2) 쓰레드 관련 문제

초기에 작성된 코드는 구조를 보면 SimpleDb 는 단순히 DriverManager.getConnection() 으로 매번 새로운 Connection 을 열고,
메서드가 끝날 때 닫아버리는 방식으로 멀티스레드 환경에서는 안전하지도 않고, 요구사항(스레드 당 1개 연결 유지)에도 맞지 않았다.

---

수정한 내용은 다음과 같다.

1. **ThreadLocal 적용**
    - `SimpleDb` 내에 `ThreadLocal<Connection>`을 두어 스레드마다 독립적인 Connection 객체를 관리하도록 변경했다.
    - `getConnection()` 호출 시 이미 존재하는 Connection은 재사용하고, 없거나 닫힌 상태면 새로 생성한다.

2. **Connection 종료 관리**
    - 각 스레드가 사용한 Connection은 `closeThreadConnection()` 메서드를 통해 명시적으로 종료하도록 분리했다.
    - SQL 실행 메서드(`run()`, `insert()`, `update()`, `delete()`, `selectRows()` 등)에서는 Connection을 닫지 않고, PreparedStatement와 ResultSet만 종료하도록 수정했다.

3. **Sql 클래스 수정**
    - 기존에는 finally 블록에서 Connection까지 닫도록 되어 있었으나, ThreadLocal 방식에 맞게 Connection 닫는 코드를 제거했다.
    - PreparedStatement와 ResultSet만 닫도록 유지하여 멀티스레드 환경에서도 안전하게 동작하도록 했다.

4. **멀티스레드 환경에서 안전한 구조 확보**
    - 스프링 WebMVC 등에서 여러 요청이 동시에 들어와도 각 요청 스레드가 독립적인 Connection을 사용하므로 충돌이나 리소스 공유 문제를 방지할 수 있다.
    - ThreadLocal을 사용함으로써 요구사항인 “스레드 당 1개의 Connection 유지” 조건을 만족한다.


---

## 7. 느낀점

기존에 사용하던 JPA, MyBatis 대신 직접 SQL을 작성하고 실행하는 방식을 도입하면서, 
JDBC의 기본 개념과 멀티스레드 환경에서의 DB 연결 관리에 대해 깊이 이해하게 되었다.
이를 통해 단순히 ORM을 사용하는 것이 아닌 작동 방식을 이해하는 시간을 가질 수 있었다.