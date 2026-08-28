package com.ainjob.ats.common;

import org.springframework.http.HttpStatus;

/** API 오류 코드. HTTP 상태 매핑은 {@link GlobalExceptionHandler} 가 정본이다. */
public enum ErrorCode {

    /** 액세스 토큰 없음 — 시큐리티 필터 체인에서 차단. */
    TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED),
    /** 토큰이 위조·만료되었거나 서명이 맞지 않음. */
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    /** 로그인 실패 (계정 없음 / 비활성 / 비밀번호 불일치 — 구분하지 않음). */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    /** 토큰은 유효하나 기업 회원 토큰이 아님 (company_id 클레임 없음 = 구직자 토큰). */
    TENANT_REQUIRED(HttpStatus.UNAUTHORIZED),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    /** 요청이 마스터에 없는 코드를 지정함 (skill / major / degree_level / position_type). */
    UNKNOWN_MASTER_CODE(HttpStatus.BAD_REQUEST),

    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    // CROSS_TENANT_ACCESS(403) 은 없앴다. 다른 테넌트의 리소스는 "권한 없음"이 아니라
    // "존재하지 않음"으로 응답한다 — 구분해 주면 식별자를 훑어 존재 여부를 수집할 수 있다.
    // 서버 로그에서의 구분은 CrossTenantAccessException 타입이 그대로 유지한다.
    /** 구직자가 <b>본인이 아닌</b> 지원자의 프로필을 조회하려 함. */
    NOT_OWN_PROFILE(HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    INVALID_STAGE_TRANSITION(HttpStatus.CONFLICT),
    STAGE_CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    /** [3-3] 동일 회사 내 동일 지원자의 동일 공고 중복 지원. */
    DUPLICATE_APPLICATION(HttpStatus.CONFLICT),
    /** 마감된 공고에 대한 작업(지원 접수 / 재마감). */
    JOB_POSTING_CLOSED(HttpStatus.CONFLICT),
    /** 그 밖의 유일성 제약 위반 (지원자 이메일, 학위 중복 등). */
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
