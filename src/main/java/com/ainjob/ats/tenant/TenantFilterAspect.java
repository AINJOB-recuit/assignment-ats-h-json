package com.ainjob.ats.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.annotation.Order;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;

/**
 * 3계층 격리 중 <b>격리 ② — "모든 쿼리에 {@code WHERE company_id=?} 자동 주입"</b> 구현체다(README 7장).
 *
 * <p>트랜잭션이 열린 뒤 그 안에서 Hibernate 필터를 켠다. 그러면 이후 그 트랜잭션에서 실행되는
 * 모든 HQL/JPQL 과 컬렉션 로딩에 조건이 자동으로 붙는다 — 개발자가 새 쿼리를 짜면서 조건을
 * 빠뜨릴 수 없다는 것이 이 방식의 목적이다.
 *
 * <p><b>왜 {@code @Order} 를 명시하는가.</b> 이 어드바이스는 반드시 트랜잭션 <i>안쪽</i>에서
 * 돌아야 한다. 바깥에서 돌면 아직 영속성 컨텍스트가 없어 켤 대상이 없다. 트랜잭션 어드바이저의
 * 순서는 {@code TransactionConfig} 가 {@code order = 0} 으로 고정하고, 이 애스펙트는 그보다
 * 낮은 우선순위(= 안쪽)를 갖는다.
 *
 * <p><b>적용되지 않는 경로 세 가지</b> — 여기서 막지 못하므로 각자 조건을 명시해야 한다.
 * <ol>
 *   <li><b>네이티브 SQL</b> — Hibernate 필터가 원천적으로 적용되지 않는다.
 *       {@code sql/passed-applicants.sql} 은 {@code :companyId} 를 직접 바인딩한다(수동 주입).</li>
 *   <li><b>PK 직접 조회</b> — {@code find()} / {@code getReference()} 및 연관 프록시 초기화에는
 *       적용되지 않는다(JPA 명세상 식별자 조회는 필터 대상이 아니다). 그래서 기업 영역 서비스는
 *       {@code findById} 대신 JPQL 조회 메서드를 쓴다.</li>
 *   <li><b>요청 스레드 밖</b> — {@code @Async} 리스너 등은 {@link TenantScope} 가 비어 있다.</li>
 * </ol>
 *
 * <p>그래서 이 애스펙트는 격리를 <b>대체</b>하지 않고 <b>이중화</b>한다. 서비스의
 * {@code isOwnedBy} 검사는 그대로 남아 2차 방어를 맡는다.
 */
@Aspect
@Component
@Order(TransactionConfig.TENANT_FILTER_ASPECT_ORDER)
public class TenantFilterAspect {

    private final EntityManagerFactory entityManagerFactory;

    public TenantFilterAspect(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)"
            + " || @within(org.springframework.transaction.annotation.Transactional)")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (TenantScope.isActive()) {
            enableOn(currentSession());
        }
        return joinPoint.proceed();
    }

    /**
     * 진행 중인 트랜잭션에 묶인 영속성 컨텍스트. 트랜잭션 밖이면 {@code null} 이다
     * (그 경우 켤 대상이 없으므로 조용히 건너뛴다).
     */
    private Session currentSession() {
        EntityManager entityManager =
                EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        return entityManager == null ? null : entityManager.unwrap(Session.class);
    }

    /** 중첩 호출에서 같은 세션에 두 번 켜지 않도록 확인한다(두 번 켜도 무해하지만 의도를 분명히 한다). */
    private static void enableOn(Session session) {
        if (session == null || session.getEnabledFilter(TenantFilters.TENANT_FILTER) != null) {
            return;
        }
        session.enableFilter(TenantFilters.TENANT_FILTER)
                .setParameter(TenantFilters.COMPANY_ID_PARAM, TenantScope.companyId());
    }
}
