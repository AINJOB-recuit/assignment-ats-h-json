package com.ainjob.ats.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>company_id 를 받지 않는다는 점이 핵심이다. 소속 기업은 계정 행에서 결정되므로
 * 호출자가 다른 테넌트를 사칭할 수단이 없다.
 */
public record LoginRequest(
        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "password는 필수입니다.")
        String password) {
}
