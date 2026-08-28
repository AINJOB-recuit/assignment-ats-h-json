package com.ainjob.ats.jobposting;

import com.ainjob.ats.domain.JobPosting;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공고 상세 응답 — 요구조건까지 함께 내려준다.
 *
 * <p>요구조건이 응답에 보이는 것이 중요하다. 합격자 필터가 "무엇을 기준으로" 판정했는지가
 * 이 값들이기 때문이다.
 */
public record JobPostingResponse(
        long jobPostingId,
        long companyId,
        String companyName,
        String positionCode,
        String positionName,
        String title,
        String content,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        boolean open,
        List<SkillItem> requiredSkills,
        List<CareerItem> requiredCareers,
        List<EducationItem> requiredEducations) {

    public record SkillItem(String code, String name) {
    }

    public record CareerItem(String positionCode, String positionName, short years) {
    }

    public record EducationItem(String degreeCode, String degreeName, String majorName) {
    }

    /**
     * 영속성 컨텍스트가 살아 있는 트랜잭션 안에서 호출해야 한다(지연 로딩 발생).
     *
     * @param now 모집 기간 판정 기준 시각. {@code open} 은 {@code is_open} 플래그가 아니라
     *            기간까지 반영한 실효 상태다
     */
    public static JobPostingResponse from(JobPosting jobPosting, LocalDateTime now) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getCompanyId(),
                jobPosting.getCompany().getName(),
                jobPosting.getPositionType().getCode(),
                jobPosting.getPositionType().getName(),
                jobPosting.getTitle(),
                jobPosting.getContent(),
                jobPosting.getOpenAt(),
                jobPosting.getCloseAt(),
                jobPosting.isOpenAt(now),
                jobPosting.getRequiredSkills().stream()
                        .map(s -> new SkillItem(s.getSkill().getCode(), s.getSkill().getName()))
                        .toList(),
                jobPosting.getRequiredCareers().stream()
                        .map(c -> new CareerItem(c.getPositionType().getCode(),
                                c.getPositionType().getName(), c.getCareerYears()))
                        .toList(),
                jobPosting.getRequiredEducations().stream()
                        .map(e -> new EducationItem(e.getDegreeLevel().getCode(),
                                e.getDegreeLevel().getName(), e.getMajor().getName()))
                        .toList());
    }
}
