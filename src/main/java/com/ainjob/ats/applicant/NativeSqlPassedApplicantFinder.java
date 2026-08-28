package com.ainjob.ats.applicant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 합격자 필터 — 과제 SQL(03_AINJOB_query.sql)을 <b>원본 파일 그대로</b> 실행하는 구현.
 *
 * <p><b>왜 JPQL 이 아니라 네이티브 SQL 인가.</b> 요구사항 1 이 "과제 SQL에서 작성한 쿼리를
 * API 엔드포인트로 구현"을 요구한다. 이 쿼리는 관계 나눗셈(스킬 AND) · 상관 서브쿼리(경력 월 합산) ·
 * NOT EXISTS AND 결합으로 짜여 있어, 그 문구가 "SQL 텍스트 그대로"를 뜻한다면 이 경로가 정답이다.
 *
 * <p><b>왜 {@code @Query(nativeQuery=true)} 가 아니라 EntityManager 인가.</b> 어노테이션 속성은
 * 컴파일 타임 상수만 받으므로 SQL 을 자바 문자열로 옮겨 적어야 한다. 그러면 제출한 .sql 파일과
 * API 가 실행하는 쿼리가 갈라질 수 있다. {@link SqlLoader} 로 파일을 읽어 그대로 넘기면
 * <b>제출물과 실행 쿼리가 같은 하나의 파일</b>로 유지된다.
 *
 * <p>이 구현은 SQL 안에서 {@code NOW()} 를 부르므로 {@code now} 인자를 쓰지 않는다.
 * 원본 쿼리를 한 글자도 고치지 않기 위해서다 — 두 구현의 기준 시각 차이는 요청 처리 시간(밀리초)
 * 수준이라 경력 <i>연수</i> 판정에는 영향이 없다.
 */
@Component
public class NativeSqlPassedApplicantFinder implements PassedApplicantFinder {

    private static final String PASSED_APPLICANTS_SQL = SqlLoader.load("sql/passed-applicants.sql");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public PassedApplicantStrategy strategy() {
        return PassedApplicantStrategy.NATIVE_SQL;
    }

    @Override
    public List<PassedApplicant> find(long companyId, long jobPostingId, LocalDateTime now) {
        // createNativeQuery 는 결과 타입을 지정해도 raw Query 를 돌려준다(JPA 스펙의 한계).
        @SuppressWarnings("unchecked")
        List<Tuple> rows = entityManager.createNativeQuery(PASSED_APPLICANTS_SQL, Tuple.class)
                .setParameter("companyId", companyId)
                .setParameter("jobPostingId", jobPostingId)
                .getResultList();

        return rows.stream()
                .map(row -> new PassedApplicant(
                        longOf(row, "application_id"),
                        longOf(row, "applicant_id"),
                        stringOf(row, "applicant_name"),
                        stringOf(row, "applicant_email"),
                        stringOf(row, "position_code"),
                        intOf(row, "career_years"),
                        shortOf(row, "current_stage_type_id"),
                        stringOf(row, "current_stage_code"),
                        stringOf(row, "current_stage_name")))
                .toList();
    }

    // 드라이버마다 정수 컬럼을 Integer/Long/BigInteger 중 무엇으로 돌려줄지가 다르다
    // (특히 DIV 연산 결과). Number 로 받아 한 번에 맞춘다.
    private static long longOf(Tuple row, String column) {
        return ((Number) row.get(column)).longValue();
    }

    private static int intOf(Tuple row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    private static short shortOf(Tuple row, String column) {
        return ((Number) row.get(column)).shortValue();
    }

    private static String stringOf(Tuple row, String column) {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }
}
