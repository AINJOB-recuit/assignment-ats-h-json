package com.ainjob.ats.applicant;

import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.Application;
import com.ainjob.ats.domain.PositionType;
import com.ainjob.ats.domain.StageType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 합격자 필터 — Spring Data JPA 구현 (기본값).
 *
 * <p>판정은 {@link PassedApplicantQueryRepository} 의 HQL 이 DB 에서 끝낸다. 이 클래스가 하는 일은
 * 통과한 지원 건을 응답 DTO 로 옮기는 것뿐이다.
 *
 * <p><b>경력연수만 자바에서 계산한다.</b> JPQL 은 SELECT 절의 상관 스칼라 서브쿼리를 허용하지
 * 않는다(원본 SQL 은 그 자리에서 경력 월수를 합산한다). 대신 이미 도메인에 있는
 * {@link Applicant#careerYearsOf} 를 쓴다 — 원본 SQL 과 같은 방식(월 단위로 합산한 뒤 12로 나눔)
 * 으로 계산하도록 짜여 있어 두 구현의 값이 어긋나지 않는다.
 */
@Component
public class JpaPassedApplicantFinder implements PassedApplicantFinder {

    private final PassedApplicantQueryRepository queryRepository;

    public JpaPassedApplicantFinder(PassedApplicantQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public PassedApplicantStrategy strategy() {
        return PassedApplicantStrategy.JPA;
    }

    @Override
    public List<PassedApplicant> find(long companyId, long jobPostingId, LocalDateTime now) {
        return queryRepository.findPassed(companyId, jobPostingId, now).stream()
                .map(application -> toDto(application, now))
                .toList();
    }

    private static PassedApplicant toDto(Application application, LocalDateTime now) {
        Applicant applicant = application.getApplicant();
        StageType currentStage = application.getCurrentStage();
        // 경력연수는 '공고가 뽑는 직무' 기준으로 집계한다(원본 SQL 의 jp.position_type_id 와 동일).
        PositionType position = application.getJobPosting().getPositionType();

        return new PassedApplicant(
                application.getId(),
                applicant.getId(),
                applicant.getName(),
                applicant.getEmail(),
                position.getCode(),
                applicant.careerYearsOf(position.getId(), now),
                currentStage.getId(),
                currentStage.getCode(),
                currentStage.getName());
    }
}
