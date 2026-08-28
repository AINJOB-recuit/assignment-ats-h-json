package com.ainjob.ats.application;

/**
 * 동일 회사 내 동일 지원자가 동일 공고에 중복 지원한 경우 → 409.
 *
 * <p>동일 공고 중복 지원 금지 제약이며, 최종 보증은 DB 의 {@code uq_application_tenant} 다.
 * 이 예외는 그 전에 걸러 원인을 분명히 알려주기 위한 것이다.
 */
public class DuplicateApplicationException extends RuntimeException {

    public DuplicateApplicationException(long jobPostingId, long applicantId) {
        super("applicant(" + applicantId + ")은(는) job_posting(" + jobPostingId + ")에 이미 지원했습니다.");
    }
}
