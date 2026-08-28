package com.ainjob.ats.applicant;

import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.JobPosting;
import com.ainjob.ats.jobposting.JobPostingRepository;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요구사항 1 — ATS 합격자 필터 유스케이스.
 *
 * <p>필터 실행 방식은 두 가지다({@link PassedApplicantStrategy}). 어느 쪽을 쓸지는 기동 설정
 * {@code ainjob.passed-applicant.strategy} 가 정하며, <b>응답은 완전히 동일하다.</b>
 *
 * <p>테넌트 검증(404/403)과 응답 조립은 전략과 무관하게 여기서 한 번만 한다. 구현체는 필터 조건만
 * 책임진다 — 전략이 늘어도 격리 규칙이 흩어지지 않는다.
 */
@Service
public class PassedApplicantService {

    private static final Logger log = LoggerFactory.getLogger(PassedApplicantService.class);

    private final Map<PassedApplicantStrategy, PassedApplicantFinder> finders =
            new EnumMap<>(PassedApplicantStrategy.class);
    private final JobPostingRepository jobPostingRepository;
    private final PassedApplicantStrategy strategy;

    public PassedApplicantService(List<PassedApplicantFinder> finders,
                                  JobPostingRepository jobPostingRepository,
                                  PassedApplicantProperties properties) {
        finders.forEach(finder -> this.finders.put(finder.strategy(), finder));
        this.jobPostingRepository = jobPostingRepository;
        this.strategy = properties.strategy();

        // 설정한 전략의 구현이 없으면 조회 시점이 아니라 기동 시점에 실패시킨다.
        if (!this.finders.containsKey(strategy)) {
            throw new IllegalStateException(
                    "합격자 필터 전략 " + strategy + " 의 구현을 찾을 수 없습니다. 등록된 전략="
                            + this.finders.keySet());
        }
        log.info("합격자 필터 실행 전략 = {} (ainjob.passed-applicant.strategy)", strategy);
    }

    /**
     * @param companyId    인증에서 확정된 테넌트. 클라이언트 입력이 아니다.
     * @param jobPostingId 조회 대상 공고 PK
     */
    @Transactional(readOnly = true)
    public PassedApplicantsResponse findPassedApplicants(long companyId, long jobPostingId) {
        // 없으면 404, 있지만 남의 회사 것이면 403 — 둘을 구분해 응답한다.
        JobPosting jobPosting = jobPostingRepository.findByIdInTenantScope(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("job_posting", jobPostingId));
        if (!jobPosting.isOwnedBy(companyId)) {
            throw new CrossTenantAccessException("job_posting", jobPostingId, companyId);
        }

        // 재직 중인 경력의 종료 시점. 쿼리 안에서 현재 시각을 부르지 않고 여기서 한 번 고정한다.
        List<PassedApplicant> items =
                finders.get(strategy).find(companyId, jobPostingId, LocalDateTime.now());

        // 합격자가 0명이어도 응답 헤더(회사명·공고명)는 공고 엔티티에서 그대로 채운다.
        return new PassedApplicantsResponse(
                companyId,
                jobPosting.getCompany().getName(),
                jobPostingId,
                jobPosting.getTitle(),
                items.size(),
                items);
    }

    /** 현재 활성 전략. 테스트와 진단용. */
    public PassedApplicantStrategy activeStrategy() {
        return strategy;
    }
}
