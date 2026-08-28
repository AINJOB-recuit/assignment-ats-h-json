package com.ainjob.ats.jobposting;

import com.ainjob.ats.domain.JobPosting;
import java.time.LocalDateTime;

/**
 * 공고 목록 1행. 요구조건은 상세 조회에서만 내려준다(목록에서 굳이 N번 더 읽지 않는다).
 *
 * <p>회사명이 들어 있는 이유는 <b>공개 목록</b> 때문이다. 구직자가 보는 목록은 여러 기업의 공고가
 * 섞여 있어서 어느 회사 공고인지가 없으면 고를 수 없다. 기업 자기 목록에서는 잉여지만, 한 형식을
 * 두 곳이 공유하는 편이 응답 두 종류를 두는 것보다 낫다.
 */
public record JobPostingSummaryResponse(
        long jobPostingId,
        long companyId,
        String companyName,
        String title,
        String positionCode,
        boolean open,
        LocalDateTime openAt,
        LocalDateTime closeAt) {

    /**
     * @param now 모집 기간 판정 기준 시각. {@code open} 은 저장된 {@code is_open} 플래그가 아니라
     *            기간까지 반영한 <b>실효 상태</b>다 — 목록에 뜨는 것과 open 값이 어긋나면 안 된다
     */
    public static JobPostingSummaryResponse from(JobPosting jobPosting, LocalDateTime now) {
        return new JobPostingSummaryResponse(
                jobPosting.getId(),
                jobPosting.getCompanyId(),
                jobPosting.getCompany().getName(),
                jobPosting.getTitle(),
                jobPosting.getPositionType().getCode(),
                jobPosting.isOpenAt(now),
                jobPosting.getOpenAt(),
                jobPosting.getCloseAt());
    }
}
