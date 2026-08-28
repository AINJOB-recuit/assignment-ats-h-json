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
import jakarta.persistence.Table;
import com.ainjob.ats.tenant.TenantFilters;
import org.hibernate.annotations.Filter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 채용공고 Aggregate 루트.
 *
 * <p>공고는 기업에 귀속된다 — 여기가 테넌트 격리의 두 번째 축이다(첫 번째는 {@link Application}).
 *
 * <p><b>요구조건을 데이터로 갖는다.</b> "3년 이상", "컴퓨터공학 학사" 같은 조건을 코드나 쿼리에
 * 하드코딩하지 않고 {@link JobPostingSkill} / {@link JobPostingCareer} / {@link JobPostingEducation}
 * 세 자식 테이블에 담는다. 합격자 필터 SQL 이 이 행들을 읽어 판정한다.
 */
@Entity
@Table(name = "job_posting")
// 격리 ② — 기업 영역 요청에서는 모든 JPQL·컬렉션 로딩에 company_id 조건이 자동으로 붙는다.
// 켜는 곳은 TenantFilterAspect 이고, 공개 조회에서는 꺼져 있다(그래야 구직자가 전체 공고를 본다).
@Filter(name = TenantFilters.TENANT_FILTER, condition = TenantFilters.COMPANY_ID_CONDITION)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** 이 공고가 뽑는 직무(BE/FE). 경력연수 집계의 기준이 된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_type_id", nullable = false)
    private PositionType positionType;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    // TEXT 컬럼이다. @Lob 을 쓰면 Hibernate 가 LONGTEXT/CLOB 을 기대해
    // ddl-auto=validate 가 스키마와 어긋난다고 판단할 수 있으므로 타입을 명시한다.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "open_dt")
    private LocalDateTime openAt;

    @Column(name = "close_dt")
    private LocalDateTime closeAt;

    @Column(name = "is_open", nullable = false)
    private boolean open = true;

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingSkill> requiredSkills = new ArrayList<>();

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingCareer> requiredCareers = new ArrayList<>();

    @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobPostingEducation> requiredEducations = new ArrayList<>();

    protected JobPosting() {
    }

    public JobPosting(Company company, PositionType positionType, String title, String content,
                      LocalDateTime openAt, LocalDateTime closeAt) {
        this.company = company;
        this.positionType = positionType;
        this.title = title;
        this.content = content;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.open = true;
    }

    /** 필수스킬 추가. 같은 스킬을 두 번 넣으면 DB 의 uq_jp_skill 이 최종적으로 거부한다. */
    public void addRequiredSkill(Skill skill) {
        requiredSkills.add(new JobPostingSkill(this, skill));
    }

    /** 요구 경력 추가. 직무당 1건이다(uq_jp_career). */
    public void addRequiredCareer(PositionType positionType, short careerYears) {
        requiredCareers.add(new JobPostingCareer(this, positionType, careerYears));
    }

    /** 요구 학력 추가. 같은 (학위, 전공) 조합은 1건이다(uq_jp_education). */
    public void addRequiredEducation(DegreeLevel degreeLevel, Major major) {
        requiredEducations.add(new JobPostingEducation(this, degreeLevel, major));
    }

    /**
     * 공고를 마감한다 — 담당자가 명시적으로 내리는 조치다.
     *
     * <p>마감은 되돌리지 않는다 — 이미 마감된 공고를 다시 마감하려 하면 호출자가 409 로 응답할 수
     * 있도록 {@code false} 를 돌려준다.
     *
     * <p><b>모집 기간이 지났는지는 보지 않고 {@code is_open} 플래그만 본다.</b> 기간이 끝난 공고를
     * 담당자가 마감하는 것은 정상적인 조치이기 때문이다 — {@link #isOpenAt} 이 판단으로만 감추고
     * 있던 상태를 플래그에 실제로 반영하는 것이며, 아래 주석의 스케줄러가 할 일을 사람이 대신하는
     * 셈이다.
     */
    public boolean close(LocalDateTime now) {
        if (!open) {
            return false;
        }
        this.open = false;
        this.closeAt = now;
        return true;
    }

    /**
     * 기준 시각에 <b>실제로 모집 중인가</b> — 담당자가 내린 {@code is_open} 플래그와 모집 기간을
     * 함께 본다.
     *
     * <pre>
     *   is_open = 1
     *   AND (open_dt  IS NULL OR open_dt  &lt;= now)   -- 아직 시작 전이면 노출하지 않는다
     *   AND (close_dt IS NULL OR close_dt &gt;  now)   -- 마감일이 지났으면 더 받지 않는다
     * </pre>
     *
     * <p><b>왜 플래그만으로는 부족한가.</b> {@code close_dt} 가 지나도 담당자가
     * {@code PATCH /close} 를 부르지 않으면 {@code is_open} 은 1 로 남는다. 그러면 마감일이 한참
     * 지난 공고가 공개 목록에 계속 뜨고 지원까지 접수된다 — 데이터와 도메인 규칙이 어긋나는
     * 상태다. 그래서 판정 시점에 기간을 함께 본다. {@code open_dt} 조건은 그 대칭으로, 시작일을
     * 미래로 잡아 예약 등록한 공고가 즉시 노출되는 것을 막는다.
     *
     * <p><b>과제 범위에서는 여기까지다.</b> 실제 서비스라면 이 조건만으로는 부족하다 —
     * 판정은 감추기만 할 뿐 {@code is_open} 컬럼은 여전히 1 이라, 이 로직을 타지 않는 경로
     * (배치, 통계 쿼리, 관리자 도구, 다른 서비스의 직접 조회)에서는 그대로 '모집 중'으로 보인다.
     * <b>스케줄러(예: {@code @Scheduled} 로 {@code close_dt < now AND is_open = 1} 인 행을 주기적으로
     * 0 으로 내리는 작업)를 두어 상태를 컬럼에 확정하는 편이 낫다.</b> 그러면 이 메서드의 기간
     * 조건은 스케줄러가 아직 돌지 않은 짧은 구간을 메우는 안전망 역할로 남는다.
     * 마감 시 지원자에게 알림을 보내야 한다면 어차피 배치가 필요하기도 하다.
     *
     * <p>플래그만 읽는 접근자를 따로 두지 않은 것은 의도적이다. {@code isOpen()} 이 있으면
     * 기간을 빠뜨린 채 호출하는 경로가 생기고, 그게 정확히 지금 닫은 구멍이다.
     */
    public boolean isOpenAt(LocalDateTime now) {
        return open
                && (openAt == null || !openAt.isAfter(now))
                && (closeAt == null || closeAt.isAfter(now));
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

    public PositionType getPositionType() {
        return positionType;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getOpenAt() {
        return openAt;
    }

    public LocalDateTime getCloseAt() {
        return closeAt;
    }


    public List<JobPostingSkill> getRequiredSkills() {
        return Collections.unmodifiableList(requiredSkills);
    }

    public List<JobPostingCareer> getRequiredCareers() {
        return Collections.unmodifiableList(requiredCareers);
    }

    public List<JobPostingEducation> getRequiredEducations() {
        return Collections.unmodifiableList(requiredEducations);
    }
}
