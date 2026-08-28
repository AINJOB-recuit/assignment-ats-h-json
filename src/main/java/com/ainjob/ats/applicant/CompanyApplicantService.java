package com.ainjob.ats.applicant;

import com.ainjob.ats.application.ApplicationRepository;
import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.JobPosting;
import com.ainjob.ats.jobposting.JobPostingRepository;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기업 담당자의 지원자 열람 유스케이스.
 *
 * <p><b>공고가 테넌트 앵커다.</b> 지원자 자체에는 company_id 가 없으므로(글로벌 풀) 지원자만으로는
 * 격리 조건을 만들 수 없다. 대신 "우리 회사 공고에 지원했는가"를 묻는다 — 그 사실은
 * {@code application(company_id, job_posting_id, applicant_id)} 한 행에 있고, 그 조합은 이미
 * UNIQUE 인덱스다. 별도 인덱스 없이 격리가 성립한다.
 *
 * <p>이 경로가 없으면 담당자는 지원자의 학력·경력을 볼 방법이 없고, 있더라도 범위를 좁히지 않으면
 * 남의 회사 지원자까지 열람하게 된다. 그 중간을 공고로 끊는다.
 */
@Service
public class CompanyApplicantService {

    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;

    public CompanyApplicantService(JobPostingRepository jobPostingRepository,
                                   ApplicationRepository applicationRepository,
                                   ApplicantRepository applicantRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
    }

    /**
     * 검증 순서: 존재(404) → 소유권(403) → 관계(404).
     *
     * <p>마지막 단계가 403 이 아니라 404 인 것에 유의. 우리 회사 공고인 것은 이미 확인됐으므로
     * 권한 문제가 아니라 "이 공고의 지원자 목록에 그런 사람이 없다"는 사실 문제다. 여기서 403 을
     * 주면 "지원은 안 했지만 그 번호의 지원자는 존재한다"를 알려 주게 된다.
     *
     * @param companyId 인증에서 확정된 테넌트. 클라이언트 입력이 아니다
     */
    @Transactional(readOnly = true)
    public ApplicantResponse findApplicantOfJobPosting(long companyId, long jobPostingId,
                                                       long applicantId, LocalDateTime now) {
        JobPosting jobPosting = jobPostingRepository.findByIdInTenantScope(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("job_posting", jobPostingId));
        if (!jobPosting.isOwnedBy(companyId)) {
            throw new CrossTenantAccessException("job_posting", jobPostingId, companyId);
        }
        if (!applicationRepository.existsApplication(companyId, jobPostingId, applicantId)) {
            throw new ResourceNotFoundException("application", applicantId);
        }

        // Applicant 는 글로벌 풀이라 테넌트 필터 대상이 아니다(company_id 컬럼 자체가 없다).
        // 열람 권한은 바로 위 existsApplication 이 "우리 공고에 지원한 사람인가"로 판정한다.
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ResourceNotFoundException("applicant", applicantId));
        return ApplicantResponse.from(applicant, now);
    }
}
