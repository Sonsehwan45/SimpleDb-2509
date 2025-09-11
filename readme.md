# 순수 JDBC로 DB Util Class 구현하기

## 구현 단위
- 테스트 초기 설정 코드
- t1 ~ t3: CRUD 기능 테스트
- t4 ~ t5: 단순 조회 테스트
- t6 ~ t9 (+ t10 ~ t11): 특정 컬럼 조회 테스트

## 구현 과정 설명
- 분석 : 테스트 코드 분석
- 설계 : 분석한 내용을 기반으로 직관적으로 알 수 있는 내용을 구현
- 구현 : 상세 구현
- 리팩토링 : 구조 개선 및 사소한 기능 추가

> `t01~ t11`까지의 테스트 케이스를, 위와 같은 구현 단위로 나누어 통과할 수 있도록 개발했습니다.  
> 문서는 각 구현 단위를 분석 -> 설계 -> 구현 -> 리팩토링 하는 순서로 작성되었습니다.
---

# 1. 테스트 초기 설정 코드

테스트 환경 초기화 시 실행되는 메서드

- `beforeAll`
- `beforeEach`
- `createArticleTable`
- `makeArticleTestData`

## 분석

### SimpleDb

1. DB 연결
    - 테스트 코드에서 별다른 메서드가 보이지 않는 것으로 보아 자동으로 DB 연결 수행
2. 생성자 매개변수
    - `(String host, String user, String passwd, String dbName)` 형태로 DB 연결 정보를 받아야 함
3. DevMode 설정
    - 로깅 여부를 제어할 수 있도록 `devMode` 플래그 필요
4. run 메서드
    - 정적 SQL과 동적 SQL 실행

## 설계

### SimpleDb

```java

@Setter
public class SimpleDb {

    private boolean devMode;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        //TODO : DB 연결
    }

    public void run(String sql) {
        //TODO : 정적 sql문 처리
    }

    public void run(String sql, Object... args) {
        //TODO : 동적 sql문 처리
    }
}

```

**설계 포인트**

1. 생성자에서 DB 연결
    - 테스트 코드에서 별도 연결 메서드 호출이 없음  
      → 생성자에서 자동 연결
    - 요구사항에 의하면, 스레드 별로 독립적인 Connection 유지되어야 함  + `.close()` 호출 전까지 연결 유지  
      → SimpleDb 객체가 Connection 1개 소유
2. 동적 SQL 처리
    - 파라미터 개수와 타입이 가변적일 수도 있다고 생각

      → `Object... args`로 받아 확장성 확보


## 구현

### SimpleDb

```java
@Setter
public class SimpleDb {

    private boolean devMode;
    private Connection conn;

    public SimpleDb(String host, String user, String passwd, String dbName) {
        //DB 연결
        try {
            String url = "jdbc:mysql://" + host + "/" + dbName + "?useSSL=false&serverTimezone=Asia/Seoul";
            this.conn = DriverManager.getConnection(url, user, passwd);
            System.out.println("DB 연결 성공");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
     }

    public void run(String sql, Object... args) {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)){
            
            //파라미터 설정
            for(int i=0; i<args.length; i++) {
                pstmt.setObject(i+1, args[i]);
            }
            
            //쿼리 실행
            int rs = pstmt.executeUpdate();
            
            //로깅
            System.out.println("[sql] " + sql);
            System.out.println("[args]" + Arrays.toString(args));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}

```
**구현 포인트**

1. `conn`을 멤버 필드로 선언
    - 요구사항에 맞게 SimpleDb 객체가 하나의 Connection을 갖도록 구현
    - 외부에서 접근할 수 없게 Private으로 선언
2. 정적 SQL 처리, 동적 SQL 처리 통합
    - `Object… args`는 파라미터를 전달하지 않아도 문제없이 동작  
      → 따라서 굳이 구분하지 않고 구현
3. 로깅 로직 추가
    - SQL 실행 과정 확인 가능

**추가 포인트**

1. run에서 조회 작업이 아니라 CRUD 쿼리만 들어온다고 생각하고 executeUpdate로 고정시킴   
   하지만, 이 부분은 확장성을 고려했을 때 추후에 보완하는게 좋아보임

## 리팩토링

### SimpleDb

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

**리팩토링 포인트**

1. DB 연결 로직 분리
    - 생성자의 가독성을 높이기 위해 `connectDb`로 DB 연결 로직 분리
2. `devMode`에 따른 로깅
    - `devMode` 플래그로 로깅 출력 제어
    - `logSql`로 로직 분리
3. `logErr` 구현
4. 공통 로직 분리
    - `setArgs`로 파라미터 바인딩 책임 위임

---

# 2. CRUD 테스트

CRUD 테스트를 위해 실행되는 메서드

- `t1`
- `t2`
- `t3`

## 분석

### SimpleDb

1. `genSql` : Sql 객체를 반환

### Sql

1. `append` : 기존 SQL 문자열 뒤에 SQL 문자열을 이어 붙이는 기능
2. `insert` : 현재 SQL문을 실행 + `AUTO_INCREMENT` 로 생성된 PK 반환
3. `update` & `delete` : 현재 SQL문을 실행 + 영향받은 행의 수를 반환

---

## 설계

### SimplDb

```java
@Setter
public class SimpleDb {

    //... 생략

    public Sql genSql() {
        return new Sql(this);
    }
}
```

**설계 포인트**

1. `genSql` 실행 시 현재 `SimpleDb` 객체 전달
    - 하나의 스레드에서 하나의 DB 연결을 사용해야 함
    - Sql 객체가 생성될 때, 이미 존재하는 SimpleDb 객체를 사용해야 함 (별도로 다른 SimpleDb 객체를 통해 DB 연결 해서는 안 됨)

### Sql

```java
public class Sql {

    private final SimpleDb db;
    StringBuffer querySb = new StringBuffer();

    public Sql(SimpleDb db) {
        this.db = db;
    }

    public Sql append(String sql, Object... args) {
        //TODO : 기존 SQL 구문에 절 추가
    }

    public long insert() {
        //TODO : SQL문을 통해 DB에 반영 후, 새로 생성된 row의 id 가져오기
    }

    public int update() {
        //TODO : SQL문을 통해 DB에 반영 후, 영향을 받은 row의 수 가져오기(즉, 수정된 row의 수)
    }

    public int delete() {
        //TODO : SQL문을 통해 DB에 반영 후, 영향을 받은 row의 수 가져오기(즉, 삭제된 row의 수)
    }
}
```

**설계 포인트**

1. 생성자 매개변수
    - `SImpleDb` 객체를 받아 기존 SimpleDb 객체를 재사용
2. `StringBuffer`
    - `append` 시 원할하게 Sql 절을 추가 가능
    - 추후 멀티 스레드 환경을 고려해 `StringBuilder`가 아닌 `StringBuffer` 를 사용

---

## 구현

### Sql

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

**구현 포인트**

1. 책임 분리
   - `Sql` : SQL 구문 빌드
   - `SimpleDb` : SQL 실행
   - insert / update / delete 내부에서 SimpleDb 메서드 호출하여 쿼리 실행
2. `List<Object> args`
   - `append` 시 파라미터를 누적 저장

### SimpleDb

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

**구현 포인트**

1. 책임 분리
    - `Sql` : SQL 구문 빌드
    - `SimpleDb` : SQL 실행
2. `update` &`delete`
    - 영향받은 row 수를 반환하므로 로직이 같아 하나의 메서드로 구현

---

# 3. 단순 조회 테스트

단순 조회 테스트를 위해 실행되는 메서드

- `t4`
- `t5`

## 분석

### Sql

1. `Map<String, Object>` or `List<Map<String, Object>>`
    - String : DB 컬럼 명
    - Object : 컬럼 값
    - Map 하나가 하나의 row

---

## 설계

### Sql

```java
public class Sql {
    
    //...생략

    public List<Map<String, Object>> selectRows() {
        // TODO : 다건 조회
    }

    public Map<String, Object> selectRow() {
        // TODO : 단건 조회
    }
}
```

### SimpleDb

```java
@Setter
public class SimpleDb {

    //...생략

    public List<Map<String, Object>> selectRows(String sql, Object... args) {
        //TODO : 다건 조회 및 결과 가공
    }

    public Map<String, Object> selectRow(String sql, Object... args) {
        //TODO : 단건 조회 및 결과 가공
    }
}

```

**설계 포인트**

1. 결과 처리 위치 결정
    - `SELECT` 문 실행 시 반환 값은 `ResultSet`
    - 1안
        - `SimpleDb`의 `select` 메서드는 `resultSet`을 반환
        - `Sql`의 `selectRows`와 `selectRow`에서 원하는 값으로 반환값으로 가공
        - 장점 : `SimpleDb` 는 단순 조회만 담당하므로 유연하게 재사용 가능
    - 2안
        - `SimpleDb`에서 `selectRows`와, `selectRow` 메서드는 가공된 데이터 반환
        - `Sql`에서는 `SimpleDb`의 반환값을 그대로 반환
        - 장점 : `Sql`에서 그대로 사용할 수 있어 편리함
    - 판단
        - 처음에는 1안으로 진행했지만 문제 발생
          - (t6~t11)모두 단건 조회를 기반으로 하여, 결과 처리 로직이 반복될 것이 예상됨
          - ResultSet 처리 중 발생하는 `SQLException`을 `Sql`와 `SimpleDb` 모두에서 처리해야 함
        - 위와같은 이유로 2안으로 다시 진행함

---

## 구현

### Sql

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

### SimpleDb

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

**구현 포인트**

1. selectRow에서 selectRows 메서드 사용
2. 컬럼 기반 매핑
    - ResultSetMetaData를 이용하여 컬럼명을 확인
    - DB 컬럼명 과 실제 값을 Map으로 매핑
3. 단건 조회 처리
    - `selectRow` 에서 `selectRows` 를 재사용하여 단건 조회 구현

---

# 4. 특정 컬럼 조회 테스트

단순 조회 테스트를 위해 실행되는 메서드

- `t6` ~ `t9`
- `t10` ~ `t11`

## 분석

### Sql

1. `selectLong` : Long 타입 컬럼값 반환
2. `selectString` : String 타입 컬럼값 반환
3. `selectBoolean` : Boolean 타입 컬럼값 반환
4. 공통 : 단건 조회 기반, 특정 컬럼값 반환

---

## 설계

```java
public class Sql {

    //...생략

    public LocalDateTime selectDatetime() {
        //TODO : 단건 조회 후 LocalDateTime 타입이면 반환
    }

    public Long selectLong() {
        //TODO : 단건 조회 후 Long 타입이면 반환
    }

    public String selectString() {
        //TODO : 단건 조회 후 String 타입이면 반환
    }

    public Boolean selectBoolean() {
        //TODO : 단건 조회 후 Boolean 타입이면 반환
    }
}
```
---

## 구현

```java
public class Sql {

    //...생략

    public LocalDateTime selectDatetime() {
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null) {
            return null;
        }
        
        for(Object value : row.values()) {
            //value의 타입이 반환값 타입과 일치하면 반환
            System.out.println(value.getClass().getName());
            if(value instanceof LocalDateTime) {
                return (LocalDateTime) value;
            }
        }
        return null;
    }

    public Long selectLong() {
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null) {
            return null;
        }
        for(Object value : row.values()) {
            System.out.println(value.getClass().getName());
            if(value instanceof Long) {
                return (Long) value;
            }
        }
        return null;
    }

    public String selectString() {
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null) {
            return null;
        }
        for(Object value : row.values()) {
            System.out.println(value.getClass().getName());
            if(value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    public Boolean selectBoolean() {
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null) {
            return null;
        }
        for(Object value : row.values()) {
            System.out.println(value.getClass().getName());
            if(value instanceof Boolean) {
                return (Boolean) value;
            }
        }
        return null;
    }
}

```

**구현 포인트**

1. 단일값 추출
    - `selectRow`를 통해 단일 row 조회한 뒤, 컬럼값 중 첫 번째 값을 반환
    - 단건 조회 후 특정 타입으로 변환
2. 타입 안정성 처리
    - `instanceof`로 값 타입을 확인 후 변환
    - DB에서 조회된 타입이 예상과 다를 경우 `null` 반환
3. 결과 없는 경우 처리
    - 조회 결과 없으면 바로 `null` 반환

---

## 리팩토링

```java
public class Sql {

    //...생략
    
    private <T> T selectColumn(Class<T> type) {
        //단건 조회
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null || row.isEmpty()) return null;
        
        //첫번째 컬럼값 가져오기
        Object value = row.values().iterator().next();

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

**리팩토링 포인트**

1. 중복 제거 및 공통화
    - 기존  `selectDatetime`, `selectLong`, `selectString`, `selectBoolean` 은
      로직은 같으나 구별해야하는 Class 타입이 달랐음
    - 제네릭을 통해 메서드에서 중복되는 로직 분리
    - `selectColumn` : `db.selectRow`  + 타입 변환 로직
2. 불필요한 for문 제거
    - 이전 구현에서는 `for(Object value : row.values())`로 구현했지만, 실제로는 첫 번째 컬럼만 필요해서 비효율적이라고 생각
    - `row.values().iterator().next()`는 순회없이 첫번째 칼럼만 가져올 수 있어 간결하게 처리 가능

---

## Boolean 일 때 예외 처리

```java
public class Sql {

    //...생략
    
    private <T> T selectColumn(Class<T> type) {
        Map<String, Object> row = db.selectRow(querySb.toString(), args.toArray());
        if(row == null || row.isEmpty()) return null;

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

        if(type.isInstance(value)) {
            return (T) value;
        }

        return null;
    }

    //...생략
}

```

**기능 추가 포인트**

1. `Long` → `Boolean` 변환 처리
    - `SELECT 1 = 1`, `SELECT 1 = 0` 같은 쿼리에서 반환값이 1, 0으로 나올 수 있음
    - Boolean 타입일 경우, Long 값을 true/false로 변환하도록 로직 추가