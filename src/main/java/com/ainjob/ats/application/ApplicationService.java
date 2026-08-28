package com.ainjob.ats.application;

import com.ainjob.ats.applicant.ApplicantRepository;
import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.Application;
import com.ainjob.ats.domain.JobPosting;
import com.ainjob.ats.domain.Stage;
import com.ainjob.ats.domain.StageType;
import com.ainjob.ats.jobposting.JobPostingClosedException;
import com.ainjob.ats.jobposting.JobPostingRepository;
import com.ainjob.ats.master.StageTypeRepository;
import com.ainjob.ats.stage.StageTransitionPolicy;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지원 유스케이스 — <b>구직자 본인이 공고에 지원한다.</b>
 *
 * <p>이 서비스가 채우는 공백은 이렇다 — 전이 API 는 <b>이미 존재하는</b> 지원 건의 단계를 옮기지만,
 * 그 지원 건을 만드는 경로가 없었다. 그래서 초기 단계 이력이 존재한다는 보장도 코드에는 없었다.
 *
 * <p><b>테넌트는 공고에서 나온다.</b> 지원자는 글로벌 풀이라 테넌트가 없고, 공고는 기업 소유다.
 * 그러니 지원 건의 소속 기업은 물어볼 것도 없이 공고 주인이다 — 요청에서 받지 않고
 * {@code jobPosting.getCompany()} 를 그대로 쓴다. 여기에는 "남의 회사 공고인가"를 판정할 일이
 * 아예 없다. 구직자에게는 모든 공고가 남의 회사 것이고, 그게 정상이기 때문이다.
 *
 * <p>{@code stage.created_by} 는 null 로 남는다. 첫 단계를 만든 행위자가 담당자가 아니라 지원자
 * 본인이기 때문이며, 누가 지원했는지는 {@code application.applicant_id} 가 갖는다.
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicantRepository applicantRepository;
    private final StageTypeRepository stageTypeRepository;
    private final StageTransitionPolicy policy;

    public ApplicationService(ApplicationRepository applicationRepository,
                              JobPostingRepository jobPostingRepository,
                              ApplicantRepository applicantRepository,
                              StageTypeRepository stageTypeRepository,
                              StageTransitionPolicy policy) {
        this.applicationRepository = applicationRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.applicantRepository = applicantRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.policy = policy;
    }

    /**
     * 지원한다 — <b>지원 건과 초기 단계 이력이 한 트랜잭션에서 함께</b> 만들어진다.
     *
     * <p>검증 순서: 존재(404) → 상태(409) → 중복(409).
     *
     * @param applicantId 토큰 subject 에서 나온 지원자. 요청 본문에는 존재하지 않는 값이다
     */
    @Transactional
    public ApplicationCreatedResponse apply(long jobPostingId, long applicantId,
                                            CreateApplicationRequest request,
                                            String applicantEmail, LocalDateTime now) {
        // findByIdInTenantScope 를 쓰면 안 된다. 여기는 구직자 본인의 지원 흐름이고, 구직자는
        // 어느 회사 공고에나 지원할 수 있다. 테넌트 필터도 이 경로에서는 꺼져 있다
        // (TenantScopeFilter 는 /api/v1/companies/** 에서만 스코프를 연다).
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("job_posting", jobPostingId));
        // is_open 플래그뿐 아니라 모집 기간까지 본다. 마감일이 지났는데 담당자가 아직 close 를
        // 부르지 않은 공고에 지원이 접수되면, 공개 목록에서 사라진 공고에 지원 건이 쌓인다.
        if (!jobPosting.isOpenAt(now)) {
            throw new JobPostingClosedException(jobPostingId, "마감된 공고에는 지원할 수 없습니다.");
        }

        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ResourceNotFoundException("applicant", applicantId));

        long companyId = jobPosting.getCompanyId();

        // [3-3] 중복 지원 금지. 최종 보증은 uq_application_tenant 이고, 동시 요청으로 이 검사를
        // 함께 통과한 경우에는 DB 가 거부한 뒤 GlobalExceptionHandler 가 같은 409 로 바꾼다.
        if (applicationRepository.existsApplication(companyId, jobPostingId, applicantId)) {
            throw new DuplicateApplicationException(jobPostingId, applicantId);
        }

        // 첫 단계는 stage_type 마스터에서 온다. 지원 코드에 'APPLIED' 나 1 이 등장하지 않는다.
        StageType initialStage = stageTypeRepository.getReferenceById(policy.initialStage().getId());

        // 처리자(actor)가 null 이다 — 담당자가 개입하지 않은, 지원자 본인의 행위이기 때문이다.
        Application application = new Application(
                jobPosting.getCompany(), jobPosting, applicant, initialStage, request.reason(), null, now);
        applicationRepository.save(application);
        // 응답에 application_id 와 stage_id 를 담아야 하므로 생성키를 이 시점에 확정한다.
        applicationRepository.flush();

        Stage createdStage = application.getStages().get(0);
        return ApplicationCreatedResponse.of(application, createdStage, applicantEmail);
    }
}
