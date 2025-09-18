# SimpleDb 모듈

간단한 JDBC 기반 DB 유틸리티.  
SQL 빌더, 트랜잭션 관리, 멀티스레드 안전한 커넥션 관리, 로깅 기능을 제공하여 반복적인 JDBC 코드를 줄이고 가독성을 높입니다.

---

## 🏗️ 프로젝트 개요

- **목표:**  
  반복되는 JDBC 보일러플레이트 제거 + 안전한 파라미터 바인딩 + 트랜잭션 & 커넥션 관리 일원화
- **주요 특징:**
  - **멀티스레드 안전:** 스레드별 독립 `Connection` 유지
  - **SQL 빌더:** `append()`, `appendIn()` 기반 안전한 SQL 생성
  - **트랜잭션 지원:** `startTransaction()`, `commit()`, `rollback()`
  - **DTO 매핑:** 조회 결과를 바로 POJO로 변환
  - **로깅:** SQL + 바인딩 파라미터 + 트랜잭션 상태 출력 (devMode)

---

## 🌐 전체 구조 개요

### 🔹 클래스 간 책임 분리
- `Sql` : **쿼리 빌드** + **결과 반환** 담당, 실제 DB 실행은 모름
- `SimpleDB` : **DB 연결 관리** + **SQL 실행** + **ResultSet 처리** 담당

### 🔄 메서드 호출 흐름 예시
```text
┌─────────────┐
│     Sql     │  ← SQL 문자열과 파라미터 구성, Map → DTO/단일 타입 변환
│ append()    │
│ appendIn()  │
└─────┬───────┘
      │
      ▼
┌─────────────┐
│     Sql     │ ← insert/update/delete/select 호출
│ insert()    │
│ selectRows()│
│ selectRow() │
└─────┬───────┘
      │
      ▼
┌─────────────┐
│  SimpleDb   │  ← JDBC Connection 관리, PreparedStatement 바인딩, SQL 실행
│ run()       │
│ insert()    │
│ update()    │
│ delete()    │
│ select()    │
└─────┬───────┘
      │
      ▼
┌─────────────┐
│    JDBC     │  ← 실제 DB 쿼리 수행
│ execute*()  │
│ ResultSet   │
└─────────────┘
      │
      ▼
┌─────────────┐
│     Sql     │  ← Map → DTO 또는 단일 컬럼 타입 변환
│ selectRow() │
│ selectRows()│
└─────────────┘

```

---

## 🧩 클래스별 역할 & 주요 메서드

### 1️⃣ SimpleDb

| 역할 | 설명 | 관련 메서드                                                                                      |
|------|------|---------------------------------------------------------------------------------------------|
| **DB 연결 관리** | JDBC Connection 생성 및 스레드별 관리 | `createConnection(url, user, passwd)`, `getConnection()`, `close()`                         |
| **스레드별 커넥션 유지** | `ThreadLocal<Connection>` 기반으로 스레드 격리 | 위와 동일 |                                                                                      |
| **트랜잭션 관리** | 명시적 트랜잭션 제어 (시작/커밋/롤백) | `startTransaction()`, `commit()`, `rollback()`                                              |
| **SQL 실행 공통 처리** | PreparedStatement 생성, 파라미터 바인딩, 예외 로깅 통합 | `run(String sql, SqlExecutor<T> executor, Object... args)`                             |
| **DML 실행** | SQL 실행 및 결과 반환 | `run(sql, args...)`, `insert(sql, args...)`, `update(sql, args...)`, `delete(sql, args...)` |
| **SELECT 실행** | 쿼리 실행 후 `List<Map<String,Object>>` 변환 반환 | `select(sql, args...)`                                                                      |
| **SQL 빌더 생성** | 체이닝 가능한 SQL 빌더 생성 | `genSql()`                                                                                  |
| **개발 모드 설정** | 로깅 여부 제어 | `setDevMode(Boolean devMode)`                                                               |

---

### 2️⃣ Sql (SQL 빌더)

| 역할 | 설명 | 관련 메서드 |
|------|------|-------------|
| **SQL 문자열 빌드** | 체이닝 방식으로 SQL + 파라미터 구성 | `append(sql, args...)`, `appendIn(sql, args...)` |
| **파라미터 누적** | 내부 `args` 리스트에 안전하게 추가 |  |
| **DML 실행 위임** | SimpleDb의 DML 메서드 호출 | `insert()`, `update()`, `delete()` |
| **SELECT 실행 위임** | SimpleDb의 `select()` 호출 후 변환 | `selectRows()`, `selectRow()` |
| **DTO 변환** | Map → DTO 변환 (Jackson ObjectMapper) | `selectRows(Class<T>)`, `selectRow(Class<T>)` |
| **단일 컬럼 조회** | 첫 번째 컬럼만 추출하여 리스트/단일 값 반환 | `selectColumnList(Class<T>)`, `selectColumnSingle(Class<T>)`(private) |
| **편의 메서드** | 자주 쓰는 단일 컬럼 타입 변환 | `selectLongs()`, `selectLong()`, `selectString()`, `selectBoolean()`, `selectDatetime()` |

---

### 3️⃣ SimpleDbLogger

| 역할 | 설명 | 관련 메서드 |
|------|------|-------------|
| **SQL 로그** | 실행 SQL 및 바인딩 파라미터 출력 | `sql(sql, args...)` |
| **오류 로그** | 일반 예외 및 SQL 실행 예외 로깅 | `error(message, e)`, `error(message, e, sql, args...)` |
| **트랜잭션 로그** | 트랜잭션 이벤트 출력 (시작/커밋/롤백) | `tx(action)` |
| **DB 연결 로그** | DB 연결/해제 성공/실패 출력 | `db(message)` |
| **개발 모드 제어** | 로그 출력 여부 결정 | `setDevMode(boo

---

## ⚡ 구현 포인트

1. **제너릭 + 함수형 인터페이스 활용**
  - `run()` 메서드에서 `SqlExecutor<T>` 함수형 인터페이스를 받아 공통 실행 로직을 캡슐화
  - 제너릭 메서드로 다양한 반환 타입 (`int`, `long`, `List<Map>` 등) 지원

2. **단일 컬럼 조회 제너릭 메서드**
  - `selectColumnList(Class<T>)`, `selectColumnSingle(Class<T>)` 에서 제너릭으로 컬럼 타입 변환
  - 반복 코드 없이 한 번의 로직으로 `Long`, `String`, `Boolean`, `LocalDateTime` 등 타입 안전하게 반환

3. **DTO 변환 제너릭 메서드**
  - `selectRows(Class<T>)`, `selectRow(Class<T>)`에서 Jackson `ObjectMapper` + 제너릭을 활용해 Map → DTO 변환
  - 추가 DTO 클래스가 생겨도 변환 로직 수정 없이 재사용 가능

4. **로깅 전담 클래스 분리**
  - `SimpleDbLogger`로 SQL, 파라미터, 트랜잭션 이벤트, 예외 로깅 책임을 캡슐화
  - `devMode` 설정으로 로깅 On/Off 제어 → 운영/개발 환경 쉽게 분리 가능
  - 멀티스레드 환경 고려해 Thread 이름 prefix 로 출력

---

## 🐛 트러블슈팅 & 설계 고민

| 구분 | 내용                                                                                                                                                                                                                                                |
|------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **고민** | `Sql`과 `SimpleDb` 사이에서 어떤 책임을 맡길지 명확하지 않았음. 특히 `ResultSet → Map<String,Object>` 변환 로직을 어디 둘지가 큰 이슈였음.                                                                                                                                             |
| **시행착오** | 초기에 `Sql`이 쿼리 빌드 + 실행 + 결과 변환까지 전부 담당 → `SQLException` 처리 위치 중복 + 테스트 코드에서 단건/다건 변환 로직 반복 → 유지보수 부담 증가                                                                                                                                            |
| **결론** | <ul><li>**Sql** → 쿼리 빌더 역할 + `SimpleDb` 결과를 반환값게 맞게 변환(`ResultSet` X) (SQL 문자열/파라미터 관리/결과 변환)</li><li>**SimpleDb** → `JDBC Connection` 관리, `PreparedStatement` 실행, `ResultSet` 변환 책임 담당</li><li> **결과** : 책임이 명확해지고 중복 코드 감소, 예외 처리 일원화</li></ul> |

---
