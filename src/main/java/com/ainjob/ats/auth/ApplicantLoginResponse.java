package com.ainjob.ats.auth;

/**
 * 구직자 로그인 응답.
 *
 * <p>기업 담당자 응답({@link LoginResponse})과 달리 {@code companyId} / {@code role} 이 없다.
 * 구직자는 테넌트에 귀속되지 않고 역할 구분도 없기 때문이다 — 토큰에 실리지 않는 값을
 * 응답에만 만들어 넣지 않는다.
 */
public record ApplicantLoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        long applicantId,
        MemberType memberType,
        String name) {
}
