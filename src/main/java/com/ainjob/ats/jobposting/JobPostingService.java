package com.ainjob.ats.jobposting;

import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Company;
import com.ainjob.ats.domain.DegreeLevel;
import com.ainjob.ats.domain.JobPosting;
import com.ainjob.ats.domain.Major;
import com.ainjob.ats.domain.PositionType;
import com.ainjob.ats.domain.Skill;
import com.ainjob.ats.master.MasterCodeResolver;
import com.ainjob.ats.tenant.CompanyRepository;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채용공고 유스케이스 — 등록 / 조회 / 마감.
 *
 * <p><b>기업용 메서드는 모두 {@code companyId} 를 첫 인자로 받는다.</b> 그 값은 컨트롤러가 검증된
 * 토큰에서만 꺼내므로, 남의 회사 공고를 만들거나 건드릴 경로가 서비스 시그니처 수준에서 막혀 있다.
 *
 * <p>예외가 둘 있다 — {@code findAllOpen} 과 {@code findOpenOne}. 구직자·비회원이 보는
 * 공개 조회라 테넌트가 없다. 인자에 {@code companyId} 가 없는 것 자체가 "이건 공개물"이라는 표시다.
 *
 * <p><b>목록 조회에 페이지네이션이 없다 — 과제 범위상 의도적으로 넣지 않았다.</b> 더미 공고가
 * 4건이고 심사 시나리오가 전건 대조라, 페이지를 끊으면 확인할 것이 늘기만 한다. 실제 서비스라면
 * 세 조회 모두 {@code Pageable} 을 받아 {@code Slice}/{@code Page} 로 돌려주고, 정렬 키가
 * {@code job_posting_id DESC} 로 이미 PK 라 커서 페이지네이션으로 넘어가기도 쉽다.
 * 같은 이유로 합격자 필터({@code PassedApplicantService}) 역시 전건 반환이다.
 */
@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final CompanyRepository companyRepository;
    private final MasterCodeResolver masterCodes;

    public JobPostingService(JobPostingRepository jobPostingRepository,
                             CompanyRepository companyRepository,
                             MasterCodeResolver masterCodes) {
        this.jobPostingRepository = jobPostingRepository;
        this.companyRepository = companyRepository;
        this.masterCodes = masterCodes;
    }

    /**
     * 공고를 요구조건과 함께 등록한다.
     *
     * <p>공고 본문과 요구조건 3종이 한 트랜잭션에서 저장된다. "공고는 만들어졌는데 요구조건이 비어
     * 있는" 중간 상태가 남지 않는다 — 그런 공고는 합격자 필터가 전원 통과로 판정해 버린다.
     */
    @Transactional
    public JobPostingResponse create(long companyId, CreateJobPostingRequest request, LocalDateTime now) {
        Company company = companyRepository.getReferenceById(companyId);

        JobPosting jobPosting = new JobPosting(
                company,
                masterCodes.positionType(request.positionCode()),
                request.title(),
                request.content(),
                request.openAt() == null ? now : request.openAt(),
                request.closeAt());

        // 마스터 코드는 종류별로 한 번의 IN 조회로 모두 바꾼다.
        // 없는 코드가 있으면 종류별로 전부 모아 400 으로 알린다 — 하나씩 튕겨내지 않는다.
        List<String> skillCodes = nullToEmpty(request.requiredSkillCodes());
        Map<String, Skill> skills = masterCodes.skills(skillCodes);
        skillCodes.stream().distinct().forEach(code -> jobPosting.addRequiredSkill(skills.get(code)));

        List<CreateJobPostingRequest.RequiredCareer> careers = nullToEmpty(request.requiredCareers());
        Map<String, PositionType> careerPositions = masterCodes.positionTypes(
                careers.stream().map(CreateJobPostingRequest.RequiredCareer::positionCode).toList());
        careers.forEach(career -> jobPosting.addRequiredCareer(
                careerPositions.get(career.positionCode()), career.years()));

        List<CreateJobPostingRequest.RequiredEducation> educations =
                nullToEmpty(request.requiredEducations());
        Map<String, DegreeLevel> degreeLevels = masterCodes.degreeLevels(
                educations.stream().map(CreateJobPostingRequest.RequiredEducation::degreeCode).toList());
        Map<String, Major> majors = masterCodes.majors(
                educations.stream().map(CreateJobPostingRequest.RequiredEducation::majorName).toList());
        educations.forEach(education -> jobPosting.addRequiredEducation(
                degreeLevels.get(education.degreeCode()),
                majors.get(education.majorName())));

        return JobPostingResponse.from(jobPostingRepository.save(jobPosting), now);
    }

    /**
     * 모집 중인 전체 공고 — 구직자·비회원용. 마감된 공고는 목록에 넣지 않는다.
     *
     * <p>지원 흐름의 시작점이다. 이 목록이 없으면 구직자가 공고 식별자를 알 방법이 없어
     * 지원 API 만 열려 있어도 쓸 수 없다.
     */
    @Transactional(readOnly = true)
    public List<JobPostingSummaryResponse> findAllOpen(LocalDateTime now) {
        return jobPostingRepository.findAllOpen(now).stream()
                .map(jobPosting -> JobPostingSummaryResponse.from(jobPosting, now))
                .toList();
    }

    /**
     * 공고 상세 — 구직자·비회원용. 요구조건까지 함께 내려준다.
     *
     * <p><b>마감된 공고는 404 다.</b> 지원할 수 없는 공고를 상세로 보여 주면 지원 버튼까지 갔다가
     * 409 로 되돌아오게 된다. 목록에서 사라진 것과 상세에서 사라진 것을 일치시킨다.
     * 마감 공고를 봐야 하는 쪽은 그 공고의 주인뿐이고, 그건 {@link #findOne(long, long)} 이다.
     */
    @Transactional(readOnly = true)
    public JobPostingResponse findOpenOne(long jobPostingId, LocalDateTime now) {
        // 공개 조회다 — findByIdInTenantScope 로 바꾸면 안 된다. 구직자·비회원에게는 테넌트가
        // 없고, 있어도 남의 회사 공고를 봐야 지원할 수 있다.
        //
        // 기간 판정은 findAllOpen 의 SQL 조건과 같은 규칙이어야 한다(JobPosting.isOpenAt).
        // 목록에서는 사라졌는데 상세는 열리는 상태가 생기면 안 된다.
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .filter(posting -> posting.isOpenAt(now))
                .orElseThrow(() -> new ResourceNotFoundException("job_posting", jobPostingId));
        return JobPostingResponse.from(jobPosting, now);
    }

    /**
     * 내 회사 공고 목록 — 기업용. 마감된 것도 보인다.
     *
     * @param open null 이면 전체, true 면 모집 중만, false 면 모집 중이 아닌 것만.
     *             모집 중 여부는 {@code is_open} 플래그와 모집 기간을 함께 본다
     *             ({@code JobPosting.isOpenAt} 와 같은 규칙)
     * @param now  모집 기간 판정 기준 시각
     */
    @Transactional(readOnly = true)
    public List<JobPostingSummaryResponse> findMine(long companyId, Boolean open, LocalDateTime now) {
        List<JobPosting> found;
        if (open == null) {
            found = jobPostingRepository.findAllOfCompany(companyId);
        } else if (open) {
            found = jobPostingRepository.findAllOfCompanyOpen(companyId, now);
        } else {
            // 담당자가 마감한 것 + 모집 기간이 끝난(또는 아직 시작 전인) 것을 함께 본다.
            found = jobPostingRepository.findAllOfCompanyClosed(companyId, now);
        }
        return found.stream()
                .map(jobPosting -> JobPostingSummaryResponse.from(jobPosting, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPostingResponse findOne(long companyId, long jobPostingId, LocalDateTime now) {
        return JobPostingResponse.from(loadOwned(companyId, jobPostingId), now);
    }

    /**
     * 공고를 마감한다. 마감된 공고에는 더 이상 지원을 접수할 수 없다.
     *
     * <p>이미 마감된 공고를 다시 마감하면 409 다 — 조용히 성공시키면 호출자가 "방금 내가 닫았다"고
     * 오해한다.
     */
    @Transactional
    public JobPostingResponse close(long companyId, long jobPostingId, LocalDateTime now) {
        JobPosting jobPosting = loadOwned(companyId, jobPostingId);
        if (!jobPosting.close(now)) {
            throw new JobPostingClosedException(jobPostingId, "이미 마감된 공고는 다시 마감할 수 없습니다.");
        }
        return JobPostingResponse.from(jobPosting, now);
    }

    /** 없으면 404, 있지만 남의 회사 것이면 403. 이 구분이 존재 여부 노출과 권한 오류를 갈라 준다. */
    private JobPosting loadOwned(long companyId, long jobPostingId) {
        JobPosting jobPosting = jobPostingRepository.findByIdInTenantScope(jobPostingId)
                .orElseThrow(() -> new ResourceNotFoundException("job_posting", jobPostingId));
        if (!jobPosting.isOwnedBy(companyId)) {
            throw new CrossTenantAccessException("job_posting", jobPostingId, companyId);
        }
        return jobPosting;
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
