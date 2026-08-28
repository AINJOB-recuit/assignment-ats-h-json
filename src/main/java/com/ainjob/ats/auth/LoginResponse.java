package com.ainjob.ats.auth;

/**
 * 기업 담당자 로그인 응답. companyId·role 은 토큰 안의 값을 그대로 노출한 것(클라이언트 표시용).
 *
 * <p>구직자 로그인은 {@link ApplicantLoginResponse} 로 응답이 갈린다 — 담을 수 있는 값이 다르다.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        long companyUserId,
        long companyId,
        MemberType memberType,
        String role,
        String name) {
}
