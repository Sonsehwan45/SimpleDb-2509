# 순수 JDBC로 DB 유틸리티 구현하기

## 구현 단위
- 테스트 초기 설정 코드
- t1 ~ t3: CRUD 기능 테스트
- t4 ~ t5: 단순 조회 테스트
- t6 ~ t9 (+ t10 ~ t11): 특정 컬럼 조회 테스트

> `t01~ t11`까지의 테스트 케이스를, 위와 같은 구현 단위로 나누어 통과할 수 있도록 개발했습니다.
---

# 1. 테스트 초기 설정 코드

## SimpleDb

<details>
<summary>SimpleDb 구현 코드</summary>

```java
@Setter
public class SimpleDb {

    private boolean devMode;
    private Connection conn;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
        connectDb(url, user, passwd);
    }

    //DB 연결
    private void connectDb(String url, String user, String passwd) {
        try {
            this.conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //DB 연결 해제
    public void close() {
        if(conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                conn = null;
            }
        }
    }

    //SQL 로그
    private void logSql(String sql, Object... args) {
        if (!devMode) {
            return;
        }
        System.out.println("========== SQL ==========");
        System.out.println("sql " + sql);
        System.out.println("args " + Arrays.toString(args));
    }

    //에러 로그
    private void logErr(SQLException e, String sql, Object... args) {
        if (!devMode) {
            return;
        }
        System.err.println("========== ERROR ==========");
        System.err.println("sql " + sql);
        System.err.println("args " + Arrays.toString(args));
        e.printStackTrace(System.err);
    }

    //파라미터 바인딩
    private void setArgs(PreparedStatement pstmt, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            pstmt.setObject(i + 1, args[i]);
        }
    }

    //SimpleDB에서 정적/동적 SQL 실행
    public void run(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setArgs(pstmt, args);

            pstmt.executeUpdate();

            logSql(sql, args);
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }
}
```
</details>


### 1. DB 연결
```java
    private boolean devMode;
    private Connection conn;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
        connectDb(url, user, passwd);
    }

    //DB 연결
    private void connectDb(String url, String user, String passwd) {
        try {
            this.conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //DB 연결 해제
    public void close() {
        if(conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            } finally {
                conn = null;
            }
        }
    }
```
- 독립 Connection X
  - 요구사항에 따르면, 하나의 SimpleDb 객체로 스레드별 Connection을 소유  해야함
  - 현재 멀티스레드 환경이 아니라 단일 스레드 환경을 가정하여 구현
  - 추후에 확장 예정
- 연결 유지
  - 요구사항에 따르면, `.close()`를 호출하기 전까지 연결이 유지  
    -> 생성자에서 Connection을 멤버 변수로 저장
  - 현재 SimpleDb 생성 시 연결이 되고 close() 되지 않아 문제가 있음
  - 그러나 연결을 유지하는 로직을 먼저 구현해보고 싶어 위와 같이 구현함 (추후에 수정 예정)

### 2. 로깅
```java
    //SQL 로그
    private void logSql(String sql, Object... args) {
        if (!devMode) {
            return;
        }
        System.out.println("========== SQL ==========");
        System.out.println("sql " + sql);
        System.out.println("args " + Arrays.toString(args));
    }

    //에러 로그
    private void logErr(SQLException e, String sql, Object... args) {
        if (!devMode) {
            return;
        }
        System.err.println("========== ERROR ==========");
        System.err.println("sql " + sql);
        System.err.println("args " + Arrays.toString(args));
        e.printStackTrace(System.err);
    }
```
- `devMode` 플래그 사용
  - 개발 환경에서만 SQL/에러 로그 출력되도록 제어
- SQL 로그(`logSql`)
  - SQL 실행 시점, 실행 구문, 바인딩 파라미터 출력
  - 디버깅 시 어떤 쿼리가 실행됐는지 직관적으로 확인 가능
- 에러 로그(`logErr`)
  - SQLException 발생 시 SQL 구문과 파라미터, 스택 트레이스를 출력
  - 문제 발생 원인을 빠르게 파악할 수 있도록 지원

### 3. 파라미터 바인딩
```java
    //파라미터 바인딩
    private void setArgs(PreparedStatement pstmt, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            pstmt.setObject(i + 1, args[i]);
        }
    }
```
- 중복 로직 메서드화
  - 추후에 계속 반복되는 파라미터 바인딩 로직을 메서드로 분리
- 가변 파라미터 처리
  - SQL 실행 시 파라미터 개수가 고정되지 않으므로, 가변 인자를 사용하여 확장성 확보

### 4. SQL 실행
```java
    //SimpleDB에서 정적/동적 SQL 실행
    public void run(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setArgs(pstmt, args);

            pstmt.executeUpdate();

            logSql(sql, args);
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }
```
- 정적/동적 SQL 통합 처리
  - 파라미터가 없는 SQL도, `Object...args`를 통해 동적 SQL과 동일하게 처리
  - 처음에는 `run(String sql)`, `run(String sql, Object... args)`로 나누어저 구현했지만,  
    위 사실을 안 후 통합함

---

# 2. CRUD 테스트

## Sql

<details>
<summary>Sql 구현 코드</summary>

```java
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
}

```
</details>

### 1. 기존 SimpleDb 사용
```java
    private final SimpleDb db;
    
    public Sql(SimpleDb db) {
        this.db = db;
    }
```
- 스레드별 연결 정보를 가지고 있는 기존 SimpleDb 객체 사용
  - `SimpleDb` 객체는 하나만 생성 예정
  - Sql 내부에서 쿼리 실행 로직이 존재하는데, 이미 존재하는 SimpleDb 객체를 사용하기 위함

### 2. SQL 빌드 및 파라미터 누적
```java
    StringBuffer querySb = new StringBuffer();
    List<Object> args = new ArrayList<Object>();

    public Sql append(String sql, Object... args) {
        querySb.append(sql).append(" ");
        if(args != null && args.length > 0) {
            this.args.addAll(Arrays.asList(args));
        }
        return this;
    }
```
- `querySb`
  - SQL 구문을 단계별로 이어 붙이기 위한 변수
  - 멀티 스레드 환경에서의 안정성을 위해 `StringBuilder`가 아닌 `StringBuffer`사용
- `args`
  - SQL에 바인딩할 파라미터를 누적해서 저장

### 3. SQL 구문 빌드 및 실행
```java
    public long insert() {
        return db.insert(querySb.toString(), args.toArray());
    }

    public int update() {
        return db.updateOrDelete(querySb.toString(), args.toArray());
    }

    public int delete() {
        return db.updateOrDelete(querySb.toString(), args.toArray());
    }
```
- 책임 분리
  - Sql : SQL 구문 빌드
  - SimpleDb : SQL 실행
  - 내부에서 SimpleDb 메서드를 호출하여 쿼리 실행

## SimpleDb

<details>
<summary>SimpleDb 구현 코드</summary>

```java
@Setter
public class SimpleDb {

	//...생략

    public Sql genSql() {
        return new Sql(this);
    }
 
    public long insert(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            //파라미터 바인딩
            setArgs(pstmt, args);

            //쿼리 실행
            pstmt.executeUpdate();
            
            //PK 얻기
            ResultSet rs = pstmt.getGeneratedKeys();
            long newId = 0;
            if(rs.next()) {
                //첫번째 행이 PK, 타입은 Long
                newId = rs.getLong(1); 
            }
						
            //로깅
            logSql(sql, args);

            return newId;
        } catch (SQLException e) {
		        logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public int updateOrDelete(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            //파라미터 바인딩
            setArgs(pstmt, args);

            //쿼리 실행
            int affectedRows = pstmt.executeUpdate();

            //로깅
            logSql(sql, args);
            
            return affectedRows;
        } catch (SQLException e) {
		        logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }
}
```
</details>

### 1. Sql 객체 생성
```java
    public Sql genSql() {
        return new Sql(this);
    }
```
- 현재 SimpleDb 객체 전달
  - Sql 객체가 생성될 때, 이미 존재하는 SimpleDb 객체를 사용하기 위함
  - SimpleDb 객체는 하나만 만들 예정이기 때문

### 2. insert
```java
    public long insert(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            //파라미터 바인딩
            setArgs(pstmt, args);

            //쿼리 실행
            pstmt.executeUpdate();
            
            //PK 얻기
            ResultSet rs = pstmt.getGeneratedKeys();
            long newId = 0;
            if(rs.next()) {
                //첫번째 행이 PK, 타입은 Long
                newId = rs.getLong(1); 
            }
						
            //로깅
            logSql(sql, args);

            return newId;
        } catch (SQLException e) {
		        logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }
```
- 생성된 데이터 PK 반환

### 3. Update & Delete
```java
    public int updateOrDelete(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){

            //파라미터 바인딩
            setArgs(pstmt, args);

            //쿼리 실행
            int affectedRows = pstmt.executeUpdate();

            //로깅
            logSql(sql, args);
            
            return affectedRows;
        } catch (SQLException e) {
		        logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }
```
- 로직 통합
  - update/delete 하나의 메서드로 처리  
    -> 두 로직 전부 영향받은 행의 수를 반환하기 때문에 가능
- 영향을 받은 행(데이터)의 수 반환

---

# 3. 단순 조회 테스트

## Sql
<details>
<summary>SimpleDb 구현 코드</summary>

```java
public class Sql {

    //...생략
    
    public List<Map<String, Object>> selectRows() {
        return db.selectRows(querySb.toString(), args.toArray());
    }

    public Map<String, Object> selectRow() {
        return db.selectRow(querySb.toString(), args.toArray());
    }
}
```
</details>

### 1. 조회
```java
    public List<Map<String, Object>> selectRows() {
        return db.selectRows(querySb.toString(), args.toArray());
    }

    public Map<String, Object> selectRow() {
        return db.selectRow(querySb.toString(), args.toArray());
    }
```
- 결과 가공 책임 SimpleDb에 위임
  - 처음에 ResultSet을 반환받고 Sql에서 결과를 매핑하려 함
  - 불편했던 점
    - t6~t11 모두 단건 조회를 기반으로 하기 때문에 결과 처리 로직이 반복됨
    - ResultSet 처리 중 SQLException이 발생할 수 있어, Sql과 SimpleDb 모두에서 예외처리를 해야함
  - 위와 같은 이유로 SimpleDb에서 `selectRows`와 `selectRow` 메서드를 모두 구현해 결과 가공 책임을 위임하는 것으로 변경

## SimpleDb
<details>
<summary>SimpleDb 구현 코드</summary>

```java
@Setter
public class SimpleDb {

    //...생략

    public List<Map<String, Object>> selectRows(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){
            
            //파라미터 바인딩
            setArgs(pstmt, args);
            
            //쿼리 실행 및 결과 처리
            try(ResultSet rs = pstmt.executeQuery()) {
                logSql(sql, args);

                //조회된 데이터 저장할 List
                List<Map<String, Object>> rows = new ArrayList<>();
                
                //컬럼 수
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                //결과 매핑
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        //컬럼명 (key)
                        String columnName = metaData.getColumnName(i);
                        
                        //컬럼 실제 값 (value)
                        Object value = rs.getObject(i);
                        
                        //하나의 데이터를 Map으로 매핑
                        row.put(columnName, value);
                    }
                    //리스트에 저장
                    rows.add(row);
                }

                return rows;
            }
        } catch (SQLException e) {
            logErr(e, sql, args);
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> selectRow(String sql, Object... args) {
        //selectRows 메서드를 재사용, List에서 첫번째 값만 가져오기
        List<Map<String,Object>> rows = selectRows(sql, args);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }
}
```
</details>

### 1. 다건 조회
```java
    public List<Map<String, Object>> selectRows(String sql, Object... args) {
    //...생략
                //조회된 데이터 저장할 List
                List<Map<String, Object>> rows = new ArrayList<>();
                
                //컬럼 수
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                
                //결과 매핑
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        //컬럼명 (key)
                        String columnName = metaData.getColumnName(i);
                        
                        //컬럼 실제 값 (value)
                        Object value = rs.getObject(i);
                        
                        //하나의 데이터를 Map으로 매핑
                        row.put(columnName, value);
                    }
                    //리스트에 저장
                    rows.add(row);
                }

                return rows;
        }
    //...생략
```
- 컬럼 기반 매핑
  - ResultSetMetaData를 이용하여 컬럼명을 확인
  - DB 컬럼명과 실제 값을 Map으로 매핑

### 2. 단건 조회
```java
    public Map<String, Object> selectRow(String sql, Object... args) {
        //selectRows 메서드를 재사용, List에서 첫번째 값만 가져오기
        List<Map<String,Object>> rows = selectRows(sql, args);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        return rows.getFirst();
    }
```
- selectRows 메서드 활용
  - 중복되는 메서드가 있으므로 selectRow 메서드를 활용
  - selectRows에서 첫번째 데이터만 얻도록 처리

---

# 4. 특정 컬럼 조회 테스트

## Sql
<details>
<summary>Sql 구현 코드</summary>

```java
public class Sql {

    //...생략
    
    private <T> T selectColumn(Class<T> type) {
        //단건 조회
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null || row.isEmpty()) return null;
        
        //첫번째 컬럼값 가져오기
        Object value = row.values().iterator().next();

        /**
         * Boolean은 타입이 일치할 때 외에도
         * Long 타입일 때도 처리해줘야 함 (1 = ture, 0 = false)
         */
        if(type == Boolean.class) {
           if(value instanceof Long) {
              return (T) Boolean.valueOf(((Long) value).longValue() != 0);
           }
        }

        //타입 일치 시 반환
        if(type.isInstance(value)) {
            return (T) value;
        }

        return null;
    }

    public LocalDateTime selectDatetime() {
        return selectColumn(LocalDateTime.class);
    }

    public Long selectLong() {
        return selectColumn(Long.class);
    }

    public String selectString() {
        return selectColumn(String.class);
    }

    public Boolean selectBoolean() {
        return selectColumn(Boolean.class);
    }
}
```
</details>

### 1. 공통 로직 분리
```java
   private <T> T selectColumn(Class<T> type) {
       
      //단건 조회
      Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
      if(row == null || row.isEmpty()) return null;
      
      //첫번째 컬럼값 가져오기
      Object value = row.values().iterator().next();
      
      /**
       * Boolean은 타입이 일치할 때 외에도
       * Long 타입일 때도 처리해줘야 함 (1 = ture, 0 = false)
       */
      if(type == Boolean.class) {
         if(value instanceof Long) {
            return (T) Boolean.valueOf(((Long) value).longValue() != 0);
         }
      }
      
      //타입 일치 시 반환
      if(type.isInstance(value)) {
         return (T) value;
      }
      
      return null;
   }
```
- 제네릭 메서드 활용
  - `Class<T> type`을 인자로 받아 원하는 타입으로 변환  
    -> 코드 중복 최소화
  - `selectLong, selectString`, `selectDatetime` 등이 이 모두 이 메서드에 의존
- Long` → `Boolean` 변환 처리
  - DB 쿼리에서 `1`, `0` 같은 값이 반환될 수 있음
    - ex) `SELECT 1 = 1` -> `1`, `SELECT 1 = 0` -> `0`
  - MySQL에서는 Boolean 컬럼이 `TINYINT(1)`으로 저장되는 경우가 많음
    - JDBC에서 Long 타입으로 매핑됨
  - 변환 로직 :
    - 0 -> false
    - 그 외 -> true
- 안정성 확보
  - `null` 또는 빈 Map일 경우 즉시 `null` 반환
  - `type.isInstance(value)` 체크 후 캐스팅하여 런타임 캐스팅 오류 방지
- 첫번째 컬럼 선택
  - 단일 컬럼 조회를 가정
  - 처음에는 for문으로 첫번째 값을 바로 반환하도록 구현헀지만, 가독성이 좋지 않았음
  - `iterator().next()`로 변경

---
# 고려했던 사항

## 1. DB 연결 관리 방식
- **고민**
  - 요구사항 
    - simpleDb 객체 하나를 여러 스레드가 공유
    - 스레드별 connection 1개
    - .close() 호출 전까지 연결 유지
  - 위 요구사항을 만족하기 위해 어떻게 구현해야할까?
- **결론**
  - 학습이 부족해 명확한 구현 방법이 떠오르지 않음
  - 우선, 단일 스레드 환경이라고 가정하고 구현
  - 연결은 유지하도록 구현
  - 다만, DB 연결이 해제되지 않는 문제가 발생할 수 있음
  - 가벼운 테스트 환경이므로 우선 진행하고, 추후 개선 예정

## 2. selectRow / selectRows 결과 처리 위치
- **고민**
  - `ResultSet` → `Map<String, Object>` 변환을 Sql에서 할지, SimpleDb에서 할지
- **시행착오**
  - Sql에서 변환하려고 했음 → SimpleDb는 단순 조회만 담당
  - 문제점
    - t6~t11 테스트는 단건 조회 기반 → 결과 처리 로직 반복 예상
    - ResultSet 처리 중 `SQLException` 발생 → Sql 클래스에서도 처리 필요
- **결론**
  - SimpleDb에서 단건/다건 조회 변환 책임을 맡음
  - Sql은 단순 호출 후 결과 반환
  - 중복 코드 감소, SQLException 처리 위치 통일

## 3. Sql ↔ SimpleDb 책임 분리
- **고민**
  - 위 상황외에도 구현 과정에서 Sql과 SimpleDb 중 어디에 로직을 둬야 하는지 고민하는 상황이 반복됨
  - 처음에는 감이 잘 잡히지 않았으나, 구현을 진행하면서 책임을 명확히 분리할 필요성을 느낌
- **결론**
  - **Sql : 쿼리 빌더 역할**
    - SQL 문자열과 파라미터 관리
    - 실제 DB 동작은 알지 못함
  - **SimpleDb : 쿼리 실행 역할**
    - JDBC Connection 관리
    - PreparedStatement 생성, 파라미터 바인딩, ResultSet 처리

---

# 9/12 리팩토링

## 리팩토링 이유 및 구조 정리

### 기존 코드 문제점
1. 책임 분리 불명확
   - `SimpleDb`에서 ResultSet 처리만 담당하고 싶었는데, `selectRow`는 단순히 List에서 첫번째 값만 가녀오는 역할
   - `Sql`과 `SimpleDb`간 역할 구분이 명확하지 않아짐

2. 중복된 쿼리 실행 로직
  - insert/updateOrDelete,selectRows 모두 `PreparedStatement`를 만들어 실행
  - 그러나 반복되는 로직을 함수로 묶지 않고 그대로 작성 
  - 이런 일로, 구조가 일관되지 않음

3. `run` 메서드 활용 부족
   - 초기 설계에서 `run`을 기반으로 모든 SQL 실행을 통일하려고 했음
   - 하지만 구현 우선순위에서 밀리면서 실제 코드에서 제대로 활용하지 못함
   
## 리팩토링 목표
- SQL 실행 공통 로직 통합 -> `SimpleDb.run()`활용
- 책임 명확화
  - `SimpleDb` : SQL 실행과 결과(ResultSet) 처리
  - `SQL` : SQL 빌드 및 호출, 결과(ResultSet X) 가공

## 리팩토링 후 구조
### SimpleDb

| 기능               | 의존         |
| ---------------- | ---------- |
| `insert`         | `run()` 활용 |
| `updateOrDelete` | `run()` 활용 |
| `select`         | `run()` 활용 |

### Sql
| 기능                                                                   | 의존                          |
| -------------------------------------------------------------------- | --------------------------- |
| `insert()`                                                           | `SimpleDb.insert()`         |
| `update()`                                                           | `SimpleDb.updateOrDelete()` |
| `delete()`                                                           | `SimpleDb.updateOrDelete()` |
| `selectRows()`                                                       | `SimpleDb.select()`         |
| `selectRow()`                                                        | `SimpleDb.select()`         |
| `selectLong() / selectString() / selectDatetime() / selectBoolean()` | `selectRow()`               |

## 리팩토링 코드


<details>
<summary>Sql 구현 코드</summary>

```java
@Setter
public class SimpleDb {

  //...생략
  
  //SQL 실행 방식 정의 (함수형 인터페이스)
  @FunctionalInterface
  private interface SqlExecutor<T> {
    T execute(PreparedStatement pstmt) throws SQLException;
  }

  //공통 SQL 실행 메서드 (제너릭)
  private <T> T run(String sql, SqlExecutor<T> executor, Object... args) {
    try(PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

      setArgs(pstmt, args);

      T result = executor.execute(pstmt);

      logSql(sql, args);

      return result;
    } catch(SQLException e) {
      logErr(e, sql, args);
      throw new RuntimeException(e);
    }
  }

  //DML 전용 run 메서드 (오버로딩)
  public void run(String sql, Object... args) {
    run(sql, PreparedStatement::executeUpdate, args);
  }


  public Sql genSql() {
    return new Sql(this);
  }

  public long insert(String sql, Object... args) {
    return run(sql, pstmt -> {
      pstmt.executeUpdate();
      try(ResultSet rs = pstmt.getGeneratedKeys()) {
        return rs.next() ? rs.getLong(1) : null;
      }
    }, args);
  }

  public int updateOrDelete(String sql, Object... args) {
    return run(sql, PreparedStatement::executeUpdate, args);
  }

  public List<Map<String, Object>> select(String sql, Object... args) {
    return run(sql, pstmt -> {
      try(ResultSet rs = pstmt.executeQuery()) {

        List<Map<String, Object>> rows = new ArrayList<>();

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while(rs.next()) {
          Map<String, Object> row = new HashMap<>();
          for(int i=1; i<=columnCount; i++) {
            row.put(metaData.getColumnName(i), rs.getObject(i));
          }
          rows.add(row);
        }
        return rows;
      }
    }, args);
  }
}

```
</details>

### 1. 함수형 인터페이스 
```java
  @FunctionalInterface
  private interface SqlExecutor<T> {
    T execute(PreparedStatement pstmt) throws SQLException;
  }
```
- SQL 실행 방법 정의
  - insert/delete/update : executeUpdate
  - select : executeQuery
  - 이렇게 구문에 따라 실행 방법이 다르므로 함수형 인터페이스를 도입해,   
    PreparedStatement를 원하는 방식으로 실행하고 결과(T)를 반환하도록 함

### 2. run 제너릭 메서드
```java
  private <T> T run(String sql, SqlExecutor<T> executor, Object... args) {
    try(PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

      setArgs(pstmt, args);

      T result = executor.execute(pstmt);

      logSql(sql, args);

      return result;
    } catch(SQLException e) {
      logErr(e, sql, args);
      throw new RuntimeException(e);
    }
  }
```
- SQL 공통 로직 처리
  - PreparedStatement 생성, 파라미터 바인딩, SQL 실행, 로그 출력, 예외 처리 담당
  - `SqlExecuter<T>`를 통해 insert/update/delete/select 등 실행 방식과 반환 타입(T)를 유연하게 지정 가능
  - 제너릭 타입(T) 사용으로 다양한 반환값을 한 메서드에서 처리

### 3. run 오버로딩
```java
  public void run(String sql, Object... args) {
    run(sql, PreparedStatement::executeUpdate, args);
  }
```
- 단순 SQL 실행용 편의 메서드
  - 테스트코드에서는 DB 초기 세팅을 위해 사용됨
  - 오버로딩을 이용하여 구현

### 4. insert/updateOrDelete/select
```java
  public List<Map<String, Object>> select(String sql, Object... args) {
    return run(sql, pstmt -> {
      try(ResultSet rs = pstmt.executeQuery()) {

        List<Map<String, Object>> rows = new ArrayList<>();

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        while(rs.next()) {
          Map<String, Object> row = new HashMap<>();
          for(int i=1; i<=columnCount; i++) {
            row.put(metaData.getColumnName(i), rs.getObject(i));
          }
          rows.add(row);
        }
        return rows;
      }
    }, args);
  }
```
- SQL 실행 + 결과 처리
  - RreparedStatment를 통해 SQL을 실행하고, 실행 결과를 호출자가 원하는 형태로 가공하여 반환


## 고려했던 사항
### 1. 추상화로 인한 메서드 호출 문제
**고민**
- `Sql`에서 `selectLong`과 같은 메서드를 호출하면, 내부적으로 SimpleDb의 `select` -> `run` 메서드를 거쳐 PreparedStatement가 실행됨
- 추상화 때문에 메서드 호출이 여러 단계로 이어지는 구조라 성능 상 괜찮을까?
- 또, 추후 멀티 스레드 환경에서 SimpleDb 객체를 공유할 경우, 안정성에 문제가 생기진 않을까 고민함

**결론**
- 이러한 고민은 성능과 멀티 스레드에 대한 학습이 부족해 생겨나는 것으로 판단됨
- AI에 따르면, 
  - 추상화 계층 때문에 호출 단계가 늘어나는 것 자체는 성능 문제로 보지 않는다
  - 스레드별 Connection을 따로 제공하면 안전하다