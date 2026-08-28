package com.ainjob.ats.tenant;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 트랜잭션 어드바이저의 순서를 고정한다.
 *
 * <p>스프링 부트의 기본값은 {@link Ordered#LOWEST_PRECEDENCE} 인데, 그러면 어떤 애스펙트도
 * 트랜잭션보다 <b>안쪽</b>에 놓을 수 없다. {@link TenantFilterAspect} 는 영속성 컨텍스트가 열린
 * 뒤에 실행돼야 하므로 순서를 명시적으로 앞당긴다.
 *
 * <pre>
 *   TenantScopeFilter (서블릿)          ← 요청 단위 스코프 개폐
 *     └ 트랜잭션 어드바이저  (order 0)   ← 여기서 영속성 컨텍스트가 열리고
 *         └ TenantFilterAspect (order 1) ← 그 안에서 Hibernate 필터를 켠다
 *             └ 서비스 메서드
 * </pre>
 *
 * <p>이 클래스를 직접 선언하면 부트의 {@code TransactionAutoConfiguration} 이 물러난다.
 * 그 외 트랜잭션 설정은 부트 기본값을 그대로 쓴다.
 */
@Configuration
@EnableTransactionManagement(order = TransactionConfig.TRANSACTION_ADVISOR_ORDER)
public class TransactionConfig {

    /** 트랜잭션 경계. 테넌트 필터보다 바깥이어야 한다. */
    public static final int TRANSACTION_ADVISOR_ORDER = 0;

    /** 테넌트 필터 활성화. 트랜잭션보다 안쪽이어야 한다(숫자가 클수록 안쪽). */
    public static final int TENANT_FILTER_ASPECT_ORDER = TRANSACTION_ADVISOR_ORDER + 1;
}
