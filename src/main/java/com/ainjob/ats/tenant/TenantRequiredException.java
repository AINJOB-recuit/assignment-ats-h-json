package com.ainjob.ats.tenant;

/**
 * 테넌트(company_id)를 확정할 수 없는 경우 → 401.
 *
 * <p>토큰 자체가 없는 경우는 시큐리티 필터 체인이 먼저 401로 끊으므로 여기까지 오지 않는다.
 * 이 예외는 "토큰은 유효하지만 company_id 클레임이 없는" 경우를 잡는 마지막 방어선이다.
 */
public class TenantRequiredException extends RuntimeException {

    public TenantRequiredException(String message) {
        super(message);
    }
}
