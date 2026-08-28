package com.ainjob.ats.jobposting;

/**
 * 마감된 공고에 대한 작업 → 409.
 *
 * <p>요청 형식은 맞지만 리소스의 현재 상태가 그 작업을 허용하지 않는 경우이므로 400 이 아니라 409 다.
 */
public class JobPostingClosedException extends RuntimeException {

    public JobPostingClosedException(long jobPostingId, String action) {
        super("job_posting(" + jobPostingId + ")은(는) 이미 마감된 공고입니다. " + action);
    }
}
