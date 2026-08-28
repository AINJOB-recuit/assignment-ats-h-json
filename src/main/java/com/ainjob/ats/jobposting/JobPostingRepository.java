package com.ainjob.ats.jobposting;

import com.ainjob.ats.domain.JobPosting;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 공고 저장소.
 *
 * <p>요구조건 컬렉션(스킬/경력/학력)은 join fetch 하지 않는다. List 컬렉션 두 개 이상을 동시에
 * fetch 하면 Hibernate 가 {@code MultipleBagFetchException} 을 던지고, 억지로 붙이면 카티션 곱이
 * 생긴다. 대신 {@code default_batch_fetch_size} 로 IN 절 배치 로딩에 맡긴다.
 *
 * <p>테넌트 조건은 메서드 이름 규칙 대신 JPQL 로 명시한다
 * (이유는 {@code ApplicationRepository} 주석 참고).
 */
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    /**
     * 기업 영역의 단건 조회 — <b>{@code findById} 대신 반드시 이 메서드를 쓴다.</b>
     *
     * <p>Hibernate 테넌트 필터는 JPQL 에는 붙지만 <b>PK 직접 조회({@code find()})에는 붙지 않는다.</b>
     * {@code findById} 로 읽으면 남의 회사 공고가 그대로 반환되고, 격리가 서비스의
     * {@code isOwnedBy} 한 겹에만 의존하게 된다. 조건이 비어 보이는 이 JPQL 이 실행될 때는
     * {@code AND company_id = ?} 가 자동으로 덧붙는다.
     *
     * @return 다른 테넌트 소유이거나 존재하지 않으면 빈 값 (호출자는 404)
     */
    @Query("select j from JobPosting j where j.id = :jobPostingId")
    Optional<JobPosting> findByIdInTenantScope(@Param("jobPostingId") long jobPostingId);

    /** 내 회사 공고 전체. idx_jp_company(company_id, is_open) 의 선두 컬럼을 그대로 탄다. */
    @Query("select j from JobPosting j where j.company.id = :companyId order by j.id desc")
    List<JobPosting> findAllOfCompany(@Param("companyId") long companyId);

    /**
     * 구직자에게 보이는 <b>모집 중인 전체 공고</b> — 회사를 가리지 않는다.
     *
     * <p>테넌트 조건이 없는 유일한 공고 조회다. 채용공고는 원래 공개물이고, 볼 수 없으면 지원할
     * 공고를 고를 수 없다. 그래서 {@code idx_jp_company} 는 타지 못하고 {@code is_open} 으로만
     * 거른다 — 공고 수는 지원 건수와 달리 크게 늘지 않으므로 여기에 인덱스를 더 두지 않았다.
     *
     * <p>조건이 {@code JobPosting.isOpenAt(now)} 와 같아야 한다 — 목록에서 사라진 것과 상세에서
     * 사라진 것이 갈리면 안 된다. 기간을 자바에서 거르지 않고 SQL 에 실은 이유는, 전체를 읽어
     * 온 뒤 버리면 마감 공고가 쌓일수록 헛읽기가 늘기 때문이다.
     */
    @Query("""
            select j
              from JobPosting j
             where j.open = true
               and (j.openAt  is null or j.openAt  <= :now)
               and (j.closeAt is null or j.closeAt >  :now)
             order by j.id desc
            """)
    List<JobPosting> findAllOpen(@Param("now") LocalDateTime now);

    /**
     * 내 회사 공고 중 <b>모집 중인 것만</b>. {@code idx_jp_company} 를 두 컬럼 모두 사용한다.
     *
     * <p>{@code is_open} 플래그만 보던 것을 {@link #findAllOpen} 과 같은 기간 조건으로 맞췄다.
     * 갈라 두면 목록에는 '모집 중'으로 뜨는데 상세 응답의 {@code open} 은 false 인 공고가 생긴다.
     */
    @Query("""
            select j
              from JobPosting j
             where j.company.id = :companyId
               and j.open = true
               and (j.openAt  is null or j.openAt  <= :now)
               and (j.closeAt is null or j.closeAt >  :now)
             order by j.id desc
            """)
    List<JobPosting> findAllOfCompanyOpen(@Param("companyId") long companyId,
                                          @Param("now") LocalDateTime now);

    /**
     * 내 회사 공고 중 <b>모집 중이 아닌 것만</b> — 담당자가 마감한 것과 기간이 끝난 것을 함께 본다.
     *
     * <p>위 조건의 정확한 여집합이다. {@code case when} 한 벌로 합치는 대신 쿼리를 둘로 나눈 것은,
     * 불리언 파라미터로 분기하는 HQL 이 읽기 어렵고 방언에 따라 번역이 달라질 수 있어서다.
     */
    @Query("""
            select j
              from JobPosting j
             where j.company.id = :companyId
               and (j.open = false
                    or (j.openAt  is not null and j.openAt  >  :now)
                    or (j.closeAt is not null and j.closeAt <= :now))
             order by j.id desc
            """)
    List<JobPosting> findAllOfCompanyClosed(@Param("companyId") long companyId,
                                            @Param("now") LocalDateTime now);
}
