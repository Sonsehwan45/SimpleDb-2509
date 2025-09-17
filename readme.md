# SimpleDb - 경량 JDBC 래퍼 라이브러리

`SimpleDb`는 순수 JDBC의 복잡성과 반복적인 코드를 줄이고, 더 직관적이고 생산적으로 데이터베이스와 상호작용할 수 있도록 설계된 경량 래퍼(Wrapper) 라이브러리입니다.

## ✨ 핵심 기능

- **메서드 체이닝 기반의 SQL 빌더**: `genSql()` 메서드를 통해 가독성 높고 동적인 SQL 쿼리를 쉽게 작성할 수 있습니다.
- **자동 객체 매핑**: `SELECT` 결과를 DTO(Data Transfer Object) 클래스 인스턴스로 자동 변환합니다. 데이터베이스의 `snake_case` 컬럼명을 Java의 `camelCase` 필드명으로 자동 매핑합니다.
- **스레드 안전성**: `ThreadLocal`을 사용하여 각 스레드에 독립적인 데이터베이스 커넥션을 할당하므로, 멀티스레드 환경에서도 안전하게 사용할 수 있습니다.
- **간편한 트랜잭션 관리**: `startTransaction()`, `commit()`, `rollback()` 메서드를 통해 트랜잭션을 명시적으로 제어할 수 있습니다.
- **개발자 모드**: `setDevMode(true)`를 설정하면 실행되는 모든 SQL 쿼리와 바인딩 파라미터를 콘솔에 출력하여 디버깅을 용이하게 합니다.

## 🚀 시작하기

### 1. `SimpleDb` 인스턴스 생성

데이터베이스 연결 정보를 사용하여 `SimpleDb` 인스턴스를 생성합니다.

```java
SimpleDb simpleDb = new SimpleDb("localhost", "user", "password", "dbname");
```

### 2. 개발자 모드 활성화 (선택 사항)

개발 중에는 SQL 로그를 확인하기 위해 개발자 모드를 활성화하는 것이 좋습니다.

```java
simpleDb.setDevMode(true);

// 개발자 모드가 활성화되면 아래와 같이 쿼리와 바인딩 값이 콘솔에 출력됩니다.
// == Raw SQL ==
// SELECT * FROM article WHERE id = ?
// == Bindings ==
// 1: 1
```

## 💡 사용 예제

### 데이터 준비

예제에서 사용할 `article` 테이블과 `Article` DTO 클래스입니다.

**SQL 테이블:**
```sql
CREATE TABLE article (
    id INT UNSIGNED NOT-NULL PRIMARY KEY AUTO_INCREMENT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    title VARCHAR(100) NOT NULL,
    `body` TEXT NOT NULL,
    member_id INT UNSIGNED NOT NULL
);
```

**Java DTO:**
```java
public class Article {
    private long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String title;
    private String body;
    private long memberId;

    // Getters and Setters
}
```

### INSERT

- `genSql()`로 SQL 빌더를 시작합니다.
- `append()`로 쿼리 조각을 추가합니다.
- `insert()`를 실행하면, 생성된 `id` (Auto Increment) 값이 반환됩니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("INSERT INTO article");
sql.append("SET created_at = NOW(),");
sql.append("updated_at = NOW(),");
sql.append("title = ?,", "제목 1");
sql.append("`body` = ?,", "내용 1");
sql.append("member_id = ?", 1);

long newId = sql.insert(); // 실행 및 생성된 ID 반환

System.out.println("새 게시물 ID: " + newId);
```

### UPDATE

- `update()`를 실행하면, 영향을 받은 row의 개수가 반환됩니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("UPDATE article");
sql.append("SET title = ?,", "수정된 제목");
sql.append("`body` = ?", "수정된 내용");
sql.append("WHERE id = ?", 1);

int affectedRows = sql.update(); // 실행 및 영향 받은 row 수 반환

System.out.println("수정된 row: " + affectedRows);
```

### DELETE

- `delete()`를 실행하면, 영향을 받은 row의 개수가 반환됩니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("DELETE FROM article WHERE id = ?", 1);

int affectedRows = sql.delete(); // 실행 및 영향 받은 row 수 반환

System.out.println("삭제된 row: " + affectedRows);
```

### SELECT

#### 단일 Row 조회 (Map)

- `selectRow()`는 결과를 `Map<String, Object>`으로 반환합니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("SELECT * FROM article WHERE id = ?", 2);

Map<String, Object> row = sql.selectRow();

System.out.println("제목: " + row.get("title"));
```

#### 단일 Row 조회 (DTO)

- `selectRow(Article.class)`는 결과를 지정된 DTO 객체로 자동 매핑하여 반환합니다.
- `member_id` (snake_case) -> `memberId` (camelCase) 자동 변환을 지원합니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("SELECT * FROM article WHERE id = ?", 2);

Article article = sql.selectRow(Article.class);

System.out.println("제목: " + article.getTitle());
```

#### 여러 Row 조회 (List<Map>)

- `selectRows()`는 결과를 `List<Map<String, Object>>`으로 반환합니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("SELECT * FROM article ORDER BY id DESC");

List<Map<String, Object>> rows = sql.selectRows();

for (Map<String, Object> row : rows) {
    System.out.println("제목: " + row.get("title"));
}
```

#### 여러 Row 조회 (List<DTO>)

- `selectRows(Article.class)`는 결과를 `List<Article>`로 반환합니다.

```java
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("SELECT * FROM article ORDER BY id DESC");

List<Article> articles = sql.selectRows(Article.class);

for (Article article : articles) {
    System.out.println("제목: " + article.getTitle());
}
```

#### 단일 값 조회

- `selectLong()`, `selectString()`, `selectDatetime()` 등 특정 타입의 단일 값을 조회할 수 있습니다.

```java
// 게시물 총 개수 조회
long count = simpleDb.genSql().append("SELECT COUNT(*) FROM article").selectLong();

// 특정 게시물의 제목 조회
String title = simpleDb.genSql().append("SELECT title FROM article WHERE id = ?", 2).selectString();
```

#### `IN` 절 사용

- `appendIn()` 메서드를 사용하여 `IN` 절을 동적으로 구성할 수 있습니다.

```java
List<Long> ids = Arrays.asList(1L, 2L, 3L);
SimpleDb.Sql sql = simpleDb.genSql();
sql.append("SELECT * FROM article WHERE id");
sql.appendIn("IN (?)", ids); // id IN (1, 2, 3) 으로 변환

List<Article> articles = sql.selectRows(Article.class);
```

### 트랜잭션 관리

- 여러 데이터베이스 작업을 하나의 원자적 단위로 묶을 수 있습니다.

```java
try {
    simpleDb.startTransaction(); // 트랜잭션 시작

    simpleDb.genSql().append("UPDATE ...").update();
    simpleDb.genSql().append("INSERT ...").insert();

    simpleDb.commit(); // 모든 작업이 성공하면 커밋
} catch (SimpleDbException e) {
    simpleDb.rollback(); // 예외 발생 시 롤백
    System.out.println("작업이 롤백되었습니다.");
} finally {
    // 트랜잭션 후에는 항상 close()를 호출하여 커넥션을 정리하는 것이 좋습니다.
    simpleDb.close();
}
```

## ⚠️ 중요: 커넥션 관리

`SimpleDb`는 `ThreadLocal`을 사용하여 스레드별로 커넥션을 관리합니다. 웹 애플리케이션과 같이 스레드 풀을 사용하는 환경에서는 **각 요청(또는 작업)이 끝날 때 반드시 `simpleDb.close()`를 호출**하여 커넥션을 닫고 `ThreadLocal` 변수를 정리해야 합니다.

`finally` 블록에서 `close()`를 호출하는 것이 가장 안전한 방법입니다.

```java
try {
    // SimpleDb를 사용한 작업 수행
    Article article = simpleDb.genSql().append("...").selectRow(Article.class);
} finally {
    simpleDb.close(); // 현재 스레드의 커넥션을 안전하게 닫습니다.
}
```

## 구현 간에 어려웠던 점
- 행 조회/매핑 조회 부분 구현이 살짝 복잡하여 어려웠습니다.

## 구현 간에 느낀 점
- 구현 시작할 때 미리 ThreadLocal로 커넥션 관리 부분을 미리 구현해두어서 후반부 개발이 쉽고 빠르게 마무리된 것 같습니다.