package com.ainjob.ats.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import com.ainjob.ats.tenant.TenantFilters;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 지원 Aggregate 루트 — 지원자와 공고를 잇고, 채용 단계의 <b>현행 상태</b>를 보유한다.
 *
 * <p><b>현행(this) + 이력({@link Stage})은 하나의 Aggregate 다.</b> 그래서 단계를 바꾸는 방법은
 * {@link #moveTo} 하나뿐이고, 그 메서드는 현재 단계 갱신과 이력 1행 추가를 <b>같이</b> 한다.
 * 이력을 남기지 않고 단계만 바꾸는 경로가 코드에 존재하지 않는다.
 *
 * <p><b>company_id 는 지원 시점에 확정된다.</b> 지원자({@link Applicant})는 글로벌 풀이라 테넌트가
 * 없고, 공고는 기업 소유다. 그 둘이 만나는 이 행에서 테넌트 귀속이 생긴다 —
 * 그래서 멀티테넌시 격리의 기준점이 이 엔티티다.
 */
@Entity
@Table(name = "application")
// 격리 ② — 기업 영역 요청에서는 모든 JPQL·컬렉션 로딩에 company_id 조건이 자동으로 붙는다.
// 구직자의 지원 접수는 남의 회사 공고에 행을 만드는 정상 흐름이므로 그쪽에서는 꺼져 있다.
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.COMPANY_ID_CONDITION)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    /** 멀티테넌시 격리키. job_posting 을 따라가지 않고 별도 컬럼으로 둬 격리 조건을 단순화한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    /** 현재 단계 스냅샷. 이력은 {@link #stages}. 둘은 항상 {@link #moveTo} 로 함께 갱신된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_type_id", nullable = false)
    private StageType currentStage;

    @Column(name = "created_dt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 전이 이력. append-only 이며 PK 순 = 발생 순이다. */
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Stage> stages = new ArrayList<>();

    protected Application() {
    }

    /**
     * 지원을 접수한다. 접수와 동시에 초기 단계 이력 1행이 만들어진다.
     *
     * @param initialStage stage_type 마스터의 첫 단계(서류접수). 코드에 하드코딩하지 않고 주입받는다.
     * @param actor        접수를 등록한 담당자. stage.created_by 로 남는다.
     *                     <b>구직자가 직접 지원하면 null</b> 이다 — 그 경우 행위자는 {@code applicant} 자신이다.
     */
    public Application(Company company, JobPosting jobPosting, Applicant applicant,
                       StageType initialStage, String reason, CompanyUser actor, LocalDateTime now) {
        this.company = company;
        this.jobPosting = jobPosting;
        this.applicant = applicant;
        this.createdAt = now;
        this.currentStage = initialStage;
        this.stages.add(new Stage(this, initialStage, reason, actor, now));
    }

    /**
     * 단계를 옮기고 그 사실을 이력에 남긴다.
     *
     * <p>현재 단계 갱신과 이력 추가가 이 메서드 안에서만 일어나므로, "현행과 이력이 어긋난 상태"가
     * 만들어질 수 없다. 전이가 <i>허용되는지</i>는 도메인 밖의 정책(StageTransitionPolicy)이 판단하고,
     * 이 메서드는 이미 판단이 끝난 결과를 반영한다.
     *
     * @return 새로 쌓인 이력 행
     */
    public Stage moveTo(StageType target, String reason, CompanyUser actor, LocalDateTime now) {
        Stage stage = new Stage(this, target, reason, actor, now);
        this.stages.add(stage);
        this.currentStage = target;
        return stage;
    }

    /**
     * 되돌릴 직전 단계 — 현재 단계와 다른 가장 최근 이력.
     *
     * <p>CANCEL(합격 취소 / 탈락 철회)이 복구 대상을 정할 때 쓴다. 이력이 곧 복구 근거다.
     */
    public Optional<StageType> findPreviousStage() {
        short currentId = currentStage.getId();
        for (int i = stages.size() - 1; i >= 0; i--) {
            StageType candidate = stages.get(i).getStageType();
            if (candidate.getId() != currentId) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** 소유 테넌트 확인. 여기서 false 면 호출자는 403 으로 응답한다. */
    public boolean isOwnedBy(long companyId) {
        return company.getId() == companyId;
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public long getCompanyId() {
        return company.getId();
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public Applicant getApplicant() {
        return applicant;
    }

    public StageType getCurrentStage() {
        return currentStage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Stage> getStages() {
        return Collections.unmodifiableList(stages);
    }
}
