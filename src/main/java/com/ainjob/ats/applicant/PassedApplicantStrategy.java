package com.ainjob.ats.applicant;

/**
 * 합격자 필터를 어떤 방식으로 실행할지.
 *
 * <p>두 구현은 <b>같은 입력에 같은 결과</b>를 내야 한다. 응답 DTO도 동일하므로 전략을 바꿔도
 * 클라이언트가 보는 JSON 은 한 글자도 달라지지 않는다. 그 동등성은 통합 테스트가 고정한다.
 */
public enum PassedApplicantStrategy {

    /**
     * Spring Data JPA (HQL). 기본값 — 도메인 모델 위에서 조건을 표현한다.
     *
     * <p>판정(학력/경력/스킬)은 네이티브 버전과 마찬가지로 <b>DB 에서</b> 수행한다.
     * 응답의 경력연수만 엔티티에서 계산한다.
     */
    JPA,

    /**
     * 과제 원본 SQL({@code sql/passed-applicants.sql})을 파일 그대로 실행.
     *
     * <p>요구사항 1 이 "과제 SQL에서 작성한 쿼리를 API 엔드포인트로 구현"을 요구한 것이
     * <b>SQL 텍스트 그대로</b>를 뜻하는 경우를 위한 경로다.
     */
    NATIVE_SQL
}
