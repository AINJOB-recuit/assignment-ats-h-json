package com.ainjob.ats.applicant;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 합격자 필터 실행 전략.
 *
 * <p>구현이 둘인 이유는 과제 문구의 해석이 갈리기 때문이다 — "과제 SQL을 엔드포인트로 구현"이
 * SQL 텍스트를 그대로 실행하라는 뜻일 수도, 그 조회 기능을 API 로 만들라는 뜻일 수도 있다.
 * 두 구현을 모두 두고 기동 설정으로 고른다.
 *
 * <p>구현체는 <b>필터 조건만</b> 책임진다. 공고 존재/소유권 검증과 응답 조립은
 * {@link PassedApplicantService} 의 몫이다.
 */
public interface PassedApplicantFinder {

    PassedApplicantStrategy strategy();

    /**
     * 공고가 요구한 학력·경력·스킬을 <b>모두</b> 충족하는 합격 상태의 지원자를 찾는다.
     *
     * @param companyId    테넌트. 어떤 경우에도 이 값 없이는 호출되지 않는다(원시 타입).
     * @param jobPostingId 조회 대상 공고
     * @param now          재직 중인 경력의 종료 시점으로 삼을 기준 시각.
     *                     쿼리 안에서 현재 시각을 부르지 않고 밖에서 넣어, 요청 한 건 안에서
     *                     기준이 고정되고 테스트가 결정적이 되게 한다.
     */
    List<PassedApplicant> find(long companyId, long jobPostingId, LocalDateTime now);
}
