package com.ainjob.ats.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 (비인증 엔드포인트).
 *
 * <p>경로 규칙은 서비스 전체와 같다 — <b>{@code /companies} 가 붙으면 기업 쪽, 없으면 구직자 쪽</b>이다.
 * 잡포털의 기본 사용자는 구직자이므로 접두어 없는 {@code /auth/login} 이 구직자 몫이다.
 *
 * <pre>
 * POST /api/v1/auth/login              (구직자)
 * { "email": "recruit@ainjob.com", "password": "..." }
 *   → { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
 *       "applicantId": 1, "memberType": "APPLICANT", "name": "김똘똘" }
 *
 * POST /api/v1/auth/companies/login    (기업 담당자)
 * { "email": "recruiter@company2.com", "password": "..." }
 *   → { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
 *       "companyUserId": 4, "companyId": 2, "memberType": "COMPANY_USER",
 *       "role": "RECRUITER", "name": "기업2 채용담당" }
 * </pre>
 *
 * <p>요청 본문은 두 경로가 같지만({@link LoginRequest}) 조회하는 계정 테이블이 다르다. 그래서
 * 구직자 계정으로 기업 로그인을 시도하면 계정을 찾지 못해 401 이다 — 회원 구분을 클라이언트가
 * 고를 여지가 없다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 구직자 로그인. */
    @PostMapping("/login")
    public ResponseEntity<ApplicantLoginResponse> loginApplicant(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginApplicant(request));
    }

    /** 기업 담당자 로그인. */
    @PostMapping("/companies/login")
    public ResponseEntity<LoginResponse> loginCompanyUser(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginCompanyUser(request));
    }
}
