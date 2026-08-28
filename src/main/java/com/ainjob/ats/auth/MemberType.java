package com.ainjob.ats.auth;

/**
 * 회원 구분 — 이 서비스의 로그인 주체는 둘이다.
 *
 * <p>구직자와 기업 담당자는 <b>테이블부터 갈라져 있다</b>({@code applicant} / {@code company_user}).
 * 한 계정이 다른 쪽 권한을 얻을 경로가 스키마 수준에서 없고, 토큰에는 어느 쪽인지가
 * {@code member_type} 클레임으로 실린다.
 *
 * <p>이 값은 {@code SecurityConfig} 에서 {@code ROLE_<이름>} 권한으로 변환된다. 그래서 인가 규칙이
 * "무슨 역할인가"(OWNER/RECRUITER/VIEWER) 이전에 <b>"어느 쪽 회원인가"</b>부터 거른다.
 */
public enum MemberType {

    /** 기업 담당자. {@code company_id} 와 {@code role} 클레임을 함께 갖는다. */
    COMPANY_USER,

    /**
     * 구직자. 어느 기업에도 귀속되지 않으므로 <b>{@code company_id} 클레임이 없다.</b>
     * 그래서 이 토큰으로는 {@link com.ainjob.ats.tenant.TenantContext} 를 통과할 수 없다.
     */
    APPLICANT
}
