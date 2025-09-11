# SimpleDb 클래스
1. 목적/역할 
 - DB 접속 커넥션 생성 + 단발성 DDL, DML 실행(run) + SQL 생성 을 담당
2. 필드 정보
 - url, user, password : JDBC 접속 정보.
 - devMode : 개발 모드 로그 출력 여부
3. 메서드
 - Connection connect()
   DriverManager.getConnection으로 호출마다 새 커넥션 생성(추후 독립적인 커넥션 사용을위해)
 - void run(String sql, Object... params)
   흐름 : connect()으로 커넥션 획득 -> 커넥션과 입력된 sql문으로 PreparedStatement 생성 
     -> setObject로 플레이스 홀더에 값 추가 -> executeUpdate로 쿼리 전송 후 역순으로 close()
   기능 : 테스트 케이스 시작전 테이블 생성
 - genSql()
   SimpleDb 바탕으로 sql 전송을 위한 클래스 생성

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
 - long insert()
   생성된 id 값을 받아오기 위해 Statement.RETURN_GENERATED_KEYS로 PreparedStatement 생성
   ResultSet 는 SQL 응답을 담기위해 생성(여기선 생성된 id 값만)
 - int update() / int delete()
   SQL문으로 변경된 데이터 행의 개수 리턴
 - Map<String,Object> selectRow()
   쿼리 결과에 대한 첫행만 반환
 - List<Map<String,Object>> selectRows()
   쿼리 결과에 대한 모든 행 순회하여 List로 반환
 - rowToMap(ResultSet rs, ResultSetMetaData meta)
   DB리소스(ResultSet/Statement/Connection)를 닫은 뒤에도 데이터를 쓰기위해서
