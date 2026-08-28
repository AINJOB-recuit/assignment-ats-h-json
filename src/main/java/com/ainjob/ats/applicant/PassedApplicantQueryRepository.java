package com.ainjob.ats.applicant;

import com.ainjob.ats.domain.Application;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 합격자 필터의 JPA(HQL) 구현.
 *
 * <p>{@code JpaRepository} 가 아니라 마커 {@link Repository} 를 상속한다. Application 용 CRUD
 * 저장소는 이미 {@code ApplicationRepository} 가 있고, 여기는 조회 쿼리 하나만 필요하기 때문이다.
 *
 * <p>세 판정 규칙(학력 OR / 경력 AND / 스킬 관계 나눗셈)을 전부 서브쿼리로 표현해
 * <b>필터링을 DB 에서</b> 끝낸다. 엔티티를 다 읽어 자바로 거르면 지원자가 늘수록 무너진다.
 */
public interface PassedApplicantQueryRepository extends Repository<Application, Long> {

    /**
     * 공고가 요구한 조건을 모두 충족하는 합격 상태의 지원 건.
     *
     * <p><b>원본 SQL과의 차이 두 가지 (의미는 동일하다)</b>
     * <ol>
     *   <li><b>{@code DIV 12} 를 없앴다.</b> 원본은 {@code 개월합 DIV 12 < 요구연차} 로 비교하는데,
     *       HQL 의 {@code /} 는 정수 나눗셈을 보장하지 않는다. 양변에 12를 곱해
     *       {@code 개월합 < 요구연차 * 12} 로 바꿨다. <b>음수가 아닌 정수</b>에서
     *       {@code floor(m/12) < y} 와 {@code m < 12y} 는 동치다.
     *
     *       <p><b>음수가 아니라는 전제가 중요하다.</b> 원본 SQL 의 {@code DIV} 와 자바의 정수
     *       나눗셈은 0 방향으로 절단하는데, 부등식 변형은 floor 를 전제한다. 두 절단 방향은
     *       음수 구간에서만 갈리므로, 개월합이 음수가 되면 이 번역이 깨지고
     *       <b>두 구현이 다른 답을 낸다</b>. 예: 요구 연차 0, 개월합 -5 →
     *       네이티브는 {@code -5 DIV 12 = 0}, {@code 0 < 0} 이 거짓이라 통과시키지만,
     *       HQL 은 {@code -5 < 0} 이 참이라 탈락시킨다.</li>
     *   <li><b>경력 한 건을 {@code case when} 으로 감쌌다.</b> 위 전제를 보증하는 장치다.
     *       아직 시작하지 않은 경력({@code startAt} 이 미래)과 기간이 뒤집힌 경력을 0개월로 본다.
     *       미래 날짜를 입력에서 막지 않기로 했으므로(입사 예정 경력 등록은 정상이다) 규칙을
     *       계산 쪽에 두었고, <b>같은 규칙이 네이티브 SQL 과 {@code Applicant.careerYearsOf} 에도
     *       똑같이 들어가 있다</b> — 셋 중 하나라도 빠지면 위 반례가 살아난다.</li>
     *   <li><b>{@code NOW()} 대신 {@code :now} 파라미터.</b> 기준 시각을 밖에서 넣어 요청 한 건
     *       안에서 고정되게 하고, 테스트를 결정적으로 만든다.</li>
     * </ol>
     *
     * <p>{@code timestampdiff} 는 표준 JPQL 이 아니라 <b>Hibernate 6 의 HQL 함수</b>다(방언별로
     * 번역된다). 순수 JPQL 만으로는 두 시각의 개월 수 차이를 구할 방법이 없다.
     */
    @Query("""
            select a
              from Application a
              join fetch a.applicant al
              join fetch a.currentStage st
             where a.company.id = :companyId
               and a.jobPosting.id = :jobPostingId
               and st.passed = true
               and exists (
                     select 1
                       from JobPostingEducation jpe, Education e
                      where jpe.jobPosting.id = :jobPostingId
                        and e.applicant = al
                        and e.degreeLevel = jpe.degreeLevel
                        and e.major = jpe.major)
               and not exists (
                     select 1
                       from JobPostingCareer jpc
                      where jpc.jobPosting.id = :jobPostingId
                        and (select coalesce(sum(
                                       case when c.startAt > :now
                                              or coalesce(c.endAt, :now) < c.startAt then 0L
                                            else timestampdiff(month, c.startAt,
                                                               coalesce(c.endAt, :now)) end), 0)
                               from Career c
                              where c.applicant = al
                                and c.positionType = jpc.positionType) < jpc.careerYears * 12)
               and (select count(distinct cs.skill.id)
                      from CareerSkill cs
                     where cs.career.applicant = al
                       and cs.skill.id in (select jps.skill.id
                                             from JobPostingSkill jps
                                            where jps.jobPosting.id = :jobPostingId))
                   = (select count(jps2)
                        from JobPostingSkill jps2
                       where jps2.jobPosting.id = :jobPostingId)
             order by a.id
            """)
    List<Application> findPassed(@Param("companyId") long companyId,
                                 @Param("jobPostingId") long jobPostingId,
                                 @Param("now") LocalDateTime now);
}
