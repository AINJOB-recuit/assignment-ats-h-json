/**
 * JPA 엔티티 — 스키마 테이블과 1:1.
 *
 * <p><b>테넌트 필터 정의가 여기 있는 이유.</b> {@code @FilterDef} 는 애플리케이션 전체에서 한 번만
 * 선언되면 되고, 두 엔티티({@link com.ainjob.ats.domain.JobPosting} ·
 * {@link com.ainjob.ats.domain.Application})가 함께 쓴다. 둘 중 하나에 얹으면 "왜 저쪽에만 있지"가
 * 되므로 패키지 수준에 둔다.
 *
 * <p>이 필터는 <b>기본적으로 꺼져 있다.</b> 요청이 {@code /api/v1/companies/**} 이고 기업 회원
 * 토큰일 때만 {@link com.ainjob.ats.tenant.TenantFilterAspect} 가 트랜잭션 안에서 켠다.
 * 공개 공고 조회와 구직자의 지원 접수는 남의 회사 공고를 읽어야 하므로 켜면 안 된다.
 */
@FilterDef(
        name = TenantFilters.TENANT_FILTER,
        parameters = @ParamDef(name = TenantFilters.COMPANY_ID_PARAM, type = Long.class))
package com.ainjob.ats.domain;

import com.ainjob.ats.tenant.TenantFilters;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
