package com.ainjob.ats.application;

import com.ainjob.ats.domain.Application;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 지원 Aggregate 저장소.
 *
 * <p>지원 접수(application 패키지)와 상태 전이(stage 패키지)가 함께 쓴다. 두 유스케이스 모두 같은
 * Aggregate 를 다루므로 저장소를 나누지 않는다.
 *
 * <p>연관을 파고드는 조건은 메서드 이름 규칙(findByCompanyId…) 대신 <b>JPQL 로 명시</b>한다.
 * 엔티티에 편의 게터 {@code getCompanyId()} 가 있어서 이름 규칙이 이를 실제 매핑 속성으로 오인할
 * 수 있는데, 그 오류는 기동 시점에야 드러난다. 경로를 직접 쓰면 그 모호함이 없다.
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * 전이 대상 지원 건을 <b>행 잠금과 함께</b> 읽는다 ({@code SELECT ... FOR UPDATE}).
     *
     * <p>왜 비관적 잠금인가 — 두 담당자가 같은 지원 건을 동시에 전이시키면 "면접→합격"과
     * "면접→불합격"이 겹쳐 이력이 어긋난다. 낙관적 잠금(@Version)을 쓰려면 스키마에 버전 컬럼을
     * 추가해야 하는데, 과제 제출물인 03_AINJOB_schema.sql 을 고치지 않는 것이 우선이라
     * <b>DDL 변경이 필요 없는 행 잠금</b>을 택했다. 잠금 구간은 단일 트랜잭션 안이라 짧다.
     *
     * <p>company_id 조건 없이 PK 로만 조회한다. 존재 여부(404)와 소유권(403)을 서비스에서 구분해
     * 응답하기 위해서다. 결과는 반드시 테넌트 검증을 거친 뒤에 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :applicationId")
    Optional<Application> findByIdForUpdate(@Param("applicationId") long applicationId);

    /**
     * [3-3] 동일 회사 내 동일 지원자가 동일 공고에 이미 지원했는지.
     *
     * <p>최종 보증은 DB 의 uq_application_tenant 이고, 이 조회는 사용자에게 원인을 분명히 알려주기
     * 위한 선검사다. 인덱스도 그 UNIQUE 를 그대로 탄다.
     */
    @Query("""
            select count(a) > 0
              from Application a
             where a.company.id = :companyId
               and a.jobPosting.id = :jobPostingId
               and a.applicant.id = :applicantId
            """)
    boolean existsApplication(@Param("companyId") long companyId,
                              @Param("jobPostingId") long jobPostingId,
                              @Param("applicantId") long applicantId);
}
