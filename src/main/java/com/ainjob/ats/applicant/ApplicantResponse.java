package com.ainjob.ats.applicant;

import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.Career;
import com.ainjob.ats.domain.PositionType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 지원자 상세 응답.
 *
 * <p>{@code careerYearsByPosition} 이 이 응답의 핵심이다. 원본 경력 행만 내려주면 클라이언트가
 * 다시 합산해야 하고, 그 합산 방식이 합격자 필터 SQL 과 어긋나면 "화면에는 3년인데 필터에서는
 * 탈락"이 된다. 그래서 <b>서버가 SQL 과 같은 방식으로 계산해 함께 내려준다.</b>
 */
public record ApplicantResponse(
        long applicantId,
        String name,
        String email,
        LocalDate birthDate,
        Boolean gender,
        List<EducationItem> educations,
        List<CareerItem> careers,
        List<CareerYears> careerYearsByPosition) {

    public record EducationItem(String degreeCode, String degreeName, String majorName, String schoolName) {
    }

    public record CareerItem(String positionCode, String companyName,
                             LocalDateTime startAt, LocalDateTime endAt,
                             boolean employed, List<String> skillCodes) {
    }

    /** 직무별 총 경력연수 — 공고의 요구 연차와 직접 비교되는 값. */
    public record CareerYears(String positionCode, String positionName, int years) {
    }

    /** 영속성 컨텍스트가 살아 있는 트랜잭션 안에서 호출해야 한다(지연 로딩 발생). */
    public static ApplicantResponse from(Applicant applicant, LocalDateTime now) {
        // 같은 직무의 경력이 여러 건이면 하나로 묶는다. 엔티티 동일성(equals)에 기대지 않고
        // 비즈니스키인 code 로 접는다.
        List<CareerYears> careerYears = applicant.getCareers().stream()
                .map(Career::getPositionType)
                .collect(Collectors.toMap(PositionType::getCode, position -> position,
                        (first, duplicate) -> first, TreeMap::new))
                .values().stream()
                .map(position -> new CareerYears(
                        position.getCode(),
                        position.getName(),
                        applicant.careerYearsOf(position.getId(), now)))
                .toList();

        return new ApplicantResponse(
                applicant.getId(),
                applicant.getName(),
                applicant.getEmail(),
                applicant.getBirthDate(),
                applicant.getGender(),
                applicant.getEducations().stream()
                        .map(e -> new EducationItem(
                                e.getDegreeLevel().getCode(),
                                e.getDegreeLevel().getName(),
                                e.getMajor().getName(),
                                e.getSchoolName()))
                        .toList(),
                applicant.getCareers().stream()
                        .map(c -> new CareerItem(
                                c.getPositionType().getCode(),
                                c.getCompanyName(),
                                c.getStartAt(),
                                c.getEndAt(),
                                c.getEndAt() == null,
                                c.getCareerSkills().stream()
                                        .map(cs -> cs.getSkill().getCode())
                                        .sorted()
                                        .toList()))
                        .toList(),
                careerYears);
    }
}
