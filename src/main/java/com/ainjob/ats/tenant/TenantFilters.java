package com.ainjob.ats.tenant;

/**
 * Hibernate 테넌트 필터의 이름 상수.
 *
 * <p>필터 정의는 {@code com.ainjob.ats.domain} 의 {@code package-info.java} 에, 적용 대상은
 * 각 엔티티의 {@code @Filter} 에, 활성화는 {@link TenantFilterAspect} 에 있다. 세 곳이 같은
 * 문자열을 써야 하므로 오타로 조용히 어긋나지 않게 상수로 묶는다.
 */
public final class TenantFilters {

    /** 격리 대상 엔티티에 {@code AND company_id = ?} 를 붙이는 필터. */
    public static final String TENANT_FILTER = "tenantFilter";

    /** 위 필터의 파라미터 이름. */
    public static final String COMPANY_ID_PARAM = "companyId";

    /** 필터 조건식. 두 엔티티가 같은 컬럼명을 쓰므로 한 곳에 둔다. */
    public static final String COMPANY_ID_CONDITION = "company_id = :companyId";

    private TenantFilters() {
    }
}
