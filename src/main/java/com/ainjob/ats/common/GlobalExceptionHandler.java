package com.ainjob.ats.common;

import com.ainjob.ats.application.DuplicateApplicationException;
import com.ainjob.ats.jobposting.JobPostingClosedException;
import com.ainjob.ats.stage.InvalidStageTransitionException;
import com.ainjob.ats.stage.StageConcurrentModificationException;
import com.ainjob.ats.applicant.NotOwnProfileException;
import com.ainjob.ats.auth.InvalidCredentialsException;
import com.ainjob.ats.auth.NotApplicantException;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import com.ainjob.ats.tenant.TenantRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 예외 → HTTP 상태/본문 매핑. {@link ErrorCode} 와 1:1로 대응하는 이 매핑이 상태 코드의 정본이다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 401 — 토큰은 유효하지만 company_id 클레임이 없는 경우. */
    @ExceptionHandler(TenantRequiredException.class)
    public ResponseEntity<ApiErrorResponse> handleTenantRequired(TenantRequiredException e, HttpServletRequest request) {
        return build(ErrorCode.TENANT_REQUIRED, e.getMessage(), request);
    }

    /** 401 — 로그인 실패. */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidCredentials(InvalidCredentialsException e,
                                                                     HttpServletRequest request) {
        return build(ErrorCode.INVALID_CREDENTIALS, e.getMessage(), request);
    }

    /** 403 — 구직자 전용 경로에 구직자 토큰이 아닌 요청이 도달함(인가 규칙과 컨트롤러의 불일치). */
    @ExceptionHandler(NotApplicantException.class)
    public ResponseEntity<ApiErrorResponse> handleNotApplicant(NotApplicantException e,
                                                               HttpServletRequest request) {
        return build(ErrorCode.ACCESS_DENIED, e.getMessage(), request);
    }

    /** 403 — 본인이 아닌 지원자의 프로필 조회. */
    @ExceptionHandler(NotOwnProfileException.class)
    public ResponseEntity<ApiErrorResponse> handleNotOwnProfile(NotOwnProfileException e,
                                                                HttpServletRequest request) {
        return build(ErrorCode.NOT_OWN_PROFILE, e.getMessage(), request);
    }

    /**
     * 404 — 다른 테넌트 리소스 접근.
     *
     * <p>응답은 "없는 리소스"와 <b>완전히 동일하다.</b> 403 으로 구분해 주면 식별자를 훑어 남의
     * 회사 리소스의 존재 여부를 알아낼 수 있기 때문이다.
     *
     * <p>다만 서버 로그에는 ERROR 로 남긴다 — 정상적으로는 테넌트 필터가 먼저 걸러 내므로 이
     * 예외가 던져졌다는 것은 <b>필터가 닿지 않는 경로로 조회했다</b>는 뜻이고, 곧 격리 버그다.
     */
    @ExceptionHandler(CrossTenantAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleCrossTenant(CrossTenantAccessException e,
                                                              HttpServletRequest request) {
        log.error("테넌트 필터가 걸러 내지 못한 교차 테넌트 접근. uri={}, 상세={}",
                request.getRequestURI(), e.getMessage());
        return build(ErrorCode.RESOURCE_NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.", request);
    }

    /** 404 — 리소스 없음. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, e.getMessage(), request);
    }

    /** 409 — 상태 전이 규칙 위반. */
    @ExceptionHandler(InvalidStageTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransition(InvalidStageTransitionException e,
                                                                    HttpServletRequest request) {
        ErrorCode code = ErrorCode.INVALID_STAGE_TRANSITION;
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.ofStageConflict(code, e.getMessage(),
                        e.getCurrentStageTypeId(), e.getRequestedStageTypeId(), request.getRequestURI()));
    }

    /** 409 — 동시 변경(낙관적 잠금 실패). */
    @ExceptionHandler(StageConcurrentModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrent(StageConcurrentModificationException e,
                                                             HttpServletRequest request) {
        return build(ErrorCode.STAGE_CONCURRENT_MODIFICATION, e.getMessage(), request);
    }

    /** 409 — [3-3] 중복 지원. */
    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateApplication(DuplicateApplicationException e,
                                                                       HttpServletRequest request) {
        return build(ErrorCode.DUPLICATE_APPLICATION, e.getMessage(), request);
    }

    /** 409 — 마감된 공고에 대한 작업. */
    @ExceptionHandler(JobPostingClosedException.class)
    public ResponseEntity<ApiErrorResponse> handleClosedJobPosting(JobPostingClosedException e,
                                                                   HttpServletRequest request) {
        return build(ErrorCode.JOB_POSTING_CLOSED, e.getMessage(), request);
    }

    /** 409 — 그 밖의 유일성 제약 위반(지원자 이메일, 학위 중복 등). */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(DuplicateResourceException e,
                                                                    HttpServletRequest request) {
        return build(ErrorCode.DUPLICATE_RESOURCE, e.getMessage(), request);
    }

    /**
     * 409 — DB 제약이 최종적으로 거부한 경우.
     *
     * <p>서비스의 선검사를 통과했더라도 동시 요청이면 DB 가 막는다. 그 상황을 500 으로 흘리지 않고
     * 선검사와 같은 409 로 맞춘다. 제약 이름 같은 내부 정보는 응답에 담지 않고 로그로만 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException e,
                                                                HttpServletRequest request) {
        log.warn("DB 제약 위반. uri={}", request.getRequestURI(), e);
        return build(ErrorCode.DUPLICATE_RESOURCE,
                "이미 존재하는 데이터이거나 제약 조건을 위반했습니다.", request);
    }

    /** 400 — 요청이 마스터에 없는 코드를 지정함. */
    @ExceptionHandler(MasterCodeNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUnknownMasterCode(MasterCodeNotFoundException e,
                                                                    HttpServletRequest request) {
        return build(ErrorCode.UNKNOWN_MASTER_CODE, e.getMessage(), request);
    }

    /** 400 — 요청 본문 검증 실패. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                             HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(" / "));
        return build(ErrorCode.VALIDATION_ERROR, message, request);
    }

    /** 400 — JSON 파싱 실패(예: transition에 정의되지 않은 값). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                             HttpServletRequest request) {
        return build(ErrorCode.MALFORMED_REQUEST,
                "요청 본문을 해석할 수 없습니다. transition은 FORWARD / REJECT / CANCEL 중 하나여야 합니다.", request);
    }

    /** 500 — 그 외. 원인은 로그로만 남기고 응답에는 내부 정보를 담지 않는다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리되지 않은 예외. uri={}", request.getRequestURI(), e);
        return build(ErrorCode.INTERNAL_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
