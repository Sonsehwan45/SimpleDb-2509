## 🎯 과제 목표

- 순수 **JDBC**로 경량 DB 유틸리티(**SimpleDb**)를 구현한다.
- **멀티스레드 환경**(예: Spring WebMVC)에서 안전하게 동작하는 **커넥션 관리**를 설계한다.
- **트랜잭션(Commit/Rollback)**, **SQL 빌더**, **DTO/엔티티 매핑** 등 핵심 기능을 스스로 설계/구현한다.
- 제공된 **단위 테스트(SimpleDbTest)** 전 항목 `통과(✅ t001~t019)`를 최종 목표로 한다.

## 🧩 요구사항 정리

## A. 스레드·커넥션 관리
- `SimpleDb` **인스턴스 1개**를 여러 스레드에서 **동시에 공유**해도 안전해야 함.
- **각 스레드는 독립적인 Connection 1개**를 사용한다.
- `simpleDb.close()` 호출 전까지 **스레드별 Connection은 유지**되어야 함.
- 구현 힌트: `ThreadLocal<Connection>` 또는 `Map<ThreadId, Connection>` + 동기화 정책.

## B. SQL 빌더(`Sql`) 기능
- `append(...)`/`appendIn(...)`을 통해 **가변 파라미터 바인딩** 및 **IN 절**을 안전하게 생성.
- 주요 실행 메서드
    - `insert()` → 생성된 **Auto Increment PK** 반환
    - `update()`, `delete()` → 영향 행 수 반환
    - 단일 값 조회: `selectLong()`, `selectString()`, `selectBoolean()`, `selectDatetime()`
    - 다중/단일 행 조회: `selectRows()`, `selectRow()`
    - 매핑 조회: `selectRows(Class<T>)`, `selectRow(Class<T>)`
- LIKE / BETWEEN / ORDER BY FIELD / LIMIT 등 조합을 **문자열 안전성**(바인딩) 유지하며 구성

## C. 트랜잭션 API
- `startTransaction()` → autoCommit=false 설정 및 트랜잭션 시작
- `commit()` / `rollback()` → 현재 스레드의 Connection 트랜잭션 제어
- 트랜잭션 경계 간 일관성 보장 (동일 스레드 내 같은 Connection 재사용)

## D. 로깅/디버그
- `setDevMode(true)`일 때 **raw SQL & 바인딩 값**을 확인 가능한 수준의 로그 출력 권장

---

## ✅ 테스트 통과 기준(요약)

- **CRUD**: `t001 insert`, `t002 update`, `t003 delete`
- **조회**: `t004 selectRows`, `t005 selectRow`, `t006 NOW()`, `t007 selectLong`, `t008 selectString`, `t009~t011 selectBoolean`
- **쿼리 도우미**: `t012 LIKE`, `t013 appendIn`, `t014 ORDER BY FIELD`, `t015~t016 DTO 매핑(Article)`
- **동시성**: `t017 multi threading` (10개 스레드 동시 조회 성공 및 **스레드별 커넥션 사용** 확인)
- **트랜잭션**: `t018 rollback`, `t019 commit`

## 기타
- 구현 과정에서 **어려웠던 점**과 **느낀 점**을 간단히 정리해주세요 (README.md 필수 항목)
- 원활한 코드 리뷰를 위해 **문제 해결 과정**을 아래 방법 중 최소 한 가지 방식으로 남겨주세요 (간단하게라도)
    - PR 문서
    - 리드미
    - 코드에 간단한 주석 추가


# 사전 학습
## JDBC
> Java DataBase Connectivity. 자바에서 데이터베이스에 접속할 수 있도록 하는 자바 API.
> JDBC는 데이터베이스에서 자료를 쿼리하거나 업데이트 하는 방법을 제공한다.

JDBC의 3가지 기능. 각 DB의 JDBC 드라이버는 JDBC 인터페이스를 구현한 구현체이다.
`Connection` : 연결
`Statement` : SQL을 담은 내용
`ResultSet` : SQL 요청 응답

JDBC에 의해 에플리케이션 로직은 JDBC API에 의존하게 되고, DB의 변경이 이루어져도 어플리케이션 서버의 코드 변경을
'최소화' 할 수 있다.
- DB마다 SQL, 데이터타입, 페이징 등의 일부 사용법이 다르기 때문에 SQL과 관련된 부분은 DB에 의존할 수 밖에 없다.

### SQLMapper, JPA

JDBC가 사용되는 부분을 추상화한 계층. 

사용자는 JDBC를 사용해서 DB에 접근하여 SQL을 실행하고 결과를 가져오는 부분은 해당 기술에 맡기고, 다른 코드를 작성하면 된다.

SQLMapper에는 대표적으로 JdbcTempalte과 MyBatis가 있다.

마찬가지로, 구현해야 할 `SimpleDb`는 JDBC 접근 부분을 JDBC 인터페이스를 이용하여 내부적으로 추상화하고

커넥션, 로깅, 내부적으로 추상화하고 append, appendIn등을 이용해

SQL을 편리하게 작성하도록 돕는 유틸리티 객체이다.

## DriverManager

DriverManager의 getConnection으로 전달되는 URL은 `jdbc:드라이버명`으로 시작되는데,

이 URL 정보를 통해 라이브러리에 등록된 JDBC 구현체(DB 드라이버)중 적절한 구현체를 찾아 커넥션을 획득해 클라이언트에 반환한다.

## 트러블 슈팅
### callback함수와 템플릿 메소드 패턴을 이용한 반복 코드 제거 (1차)

sql을 실행시키는 run 메소드에서 getConnection -> prepareStatement 바인딩 -> resultSet반환이 반복되었다.

```java
private <T> T runTemplate(String sql, Object[] args, Function<Statement, T> callback) {
    try (Connection connection = getConnection(); 
         PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        
        // 바인딩할 값이 없을 경우 생략
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
        }

        return callback.apply(statement);

    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}

// !! 컴파일에러 !! runTemplate에서 try - catch로 콜백 메소드를 감싸지만 의미 없는 try - catch 구문이 필요
public void run(String sql) {
    runTemplate(sql, statement -> statement.executeUpdate(sql));
}
```

### try - catch 제거를 위한 함수형 인터페이스 선언
```java
// Function<Statemen, T> callback을 대체하면
@FunctionalInterface
interface StatementCallback<T> {
    T apply(PreparedStatement statement) throws SQLException;
}

// 컴파일 에러가 발생하지 않는다!
public void run(String sql) {
  runTemplate(sql, statement -> statement.executeUpdate(sql));
}
```