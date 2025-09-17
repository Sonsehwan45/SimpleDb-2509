#MySQL/JDBC 유틸리티 클래스 SimpleDb 구현

# SimpleDb 클래스
1. 목적/역할 
 - DB 접속 커넥션 생성 + 단발성 DDL, DML 실행(run) + SQL 생성 을 담당
 - 트랜잭션 관리
2. 필드 정보
 - url, user, password : JDBC 접속 정보.
 - devMode : 개발 모드 로그 출력 여부
 - txConn : 스레드별 트랜잭션 커넥션 저장소
3. 메서드
 - Connection connect()
   DriverManager.getConnection으로 호출마다 새 커넥션 생성(추후 독립적인 커넥션 사용을위해)
 - void run(String sql, Object... params)
   흐름 : connect()으로 커넥션 획득 -> 커넥션과 입력된 sql문으로 PreparedStatement 생성 
     -> setObject로 플레이스 홀더에 값 추가 -> executeUpdate로 쿼리 전송 후 역순으로 close()
   기능 : 테스트 케이스 시작전 테이블 생성
 - genSql()
   SimpleDb 바탕으로 sql 전송을 위한 클래스 생성
 - void startTransaction()
   txConn 에 있는 스레드 트랜잭션 시작
 - void close()
   트랜잭션 커넥션이 남아있으면 정리
 - void commit()
   커밋 후 커넥션 정리
 - void rollback()
   롤백 후 커넥션 정리
 - Connection uncloseable()
   트랜잭션 유지를 위해 close 호출을 무시하는 프록시 생성

# Sql 클래스
1. 목적/역할
 - append()로 SQL 조각 + 바인딩 파라미터 누적
 - insert()/update()/delete()/selectRow()/selectRows()로 sql문 실행
2. 필드 정보
 - DB 접속을 위한 SimpleDb 정보
 - StringBuilder sb와 List<Object> params에 각각 SQL과 바인딩 값을 순서대로 누적.
3. 메서드
 - Sql append(String sqlPart, Object... values)
   sb.append(" ") sql문 구분을 위한 공백
   values는 내부 params에 그대로 누적 → 항상 ? 플레이스홀더와 함께 사용해야 안전(SQL 인젝션 방지).
 - Sql appendIn(String sqlPart, Object... values)
   sqlPart에 포함된 "?" 를 values의 길이에 맞춰 ?, 를 확장
 - long insert()
   생성된 id 값을 받아오기 위해 Statement.RETURN_GENERATED_KEYS로 PreparedStatement 생성
   ResultSet 는 SQL 응답을 담기위해 생성(여기선 생성된 id 값만)
 - int update() / int delete()
   SQL문으로 변경된 데이터 행의 개수 리턴
 - LocalDateTime selectDatetime()
   첫 행의 첫 컬럼을 LocalDateTime으로 반환
 - Long selectLong()
   첫 행의 첫 컬럼을 Long으로 반환
 - List<Long> selectLongs()
   모든 행의 첫 컬럼을 Long 리스트로 반환
 - String selectString()
   첫 행의 첫 컬럼을 String로 반환
 - boolean selectBoolean()
   첫 행의 첫 컬럼을 boolean 으로 반환
 - Map<String,Object> selectRow()
   쿼리 결과에 대한 첫행만 반환
 - List<Map<String,Object>> selectRows()
   쿼리 결과에 대한 모든 행 순회하여 List로 반환
 - rowToMap(ResultSet rs)
   DB리소스(ResultSet/Statement/Connection)를 닫은 뒤에도 데이터를 쓰기위해서 Map으로 변환
 - <T> List<T> selectRows(Class<T> type)
   입력받은 type의 DTO로 매핑해서 쿼리 결과의 모든 행 반환
 - <T> T selectRow(Class<T> type)
   입력받은 type의 DTO로 매핑해서 첫행만 반환
 
# 어려웠던 점 & 느낀 점
 - 프레임워크 대신 순수 JDBC로 직접 쿼리를 작성, 실행해 보니 커넥션의 생명주기부터
   트랜잭션의 경계와 예외 처리, 멀티스레드 에서의 안전한 커넥션 사용까지 세세한 부분을모두
   관리해야 해서 복잡했던 것 같습니다.
 - 평소 프레임워크가 해결해 주던 것들이 얼마나 복잡했는지 체감할 수 있었고 직접 구현해 보니
   작동 방식에 대해 생각하는 시간을 가질 수 있었다.
