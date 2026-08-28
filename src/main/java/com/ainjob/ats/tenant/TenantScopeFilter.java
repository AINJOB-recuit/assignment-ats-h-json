package com.ainjob.ats.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 기업 테넌트 범위를 열고 닫는 서블릿 필터 — 테넌트 필터 자동 주입의 <b>유일한 진입점</b>이다.
 *
 * <p><b>왜 경로로 판단하는가.</b> "기업 회원 토큰이면 켠다"로 하면 기업 담당자가 로그인한 채로
 * 공개 공고 목록({@code GET /api/v1/job-postings})을 불렀을 때 자기 회사 공고만 보인다. 공개
 * 게시판인데도 그렇다. {@code JobPostingService} 의 공개 조회와 기업 조회가 같은 엔티티를 읽기
 * 때문이다. 그래서 <b>"누가 부르는가"가 아니라 "어느 영역을 부르는가"</b>로 정한다 —
 * {@code SecurityConfig} 의 "경로가 곧 대상 독자" 원칙과 같은 기준이다.
 *
 * <p>스프링 시큐리티 체인 <b>안쪽</b>에서 실행된다(빈으로 등록된 필터의 기본 순서가 시큐리티
 * 체인보다 뒤). 그래서 여기서는 이미 토큰 검증이 끝나 있고 {@code SecurityContext} 를 읽을 수 있다.
 *
 * <p>스코프를 열지 못해도 요청을 막지는 않는다. 그 경우 테넌트 필터가 꺼진 채로 진행되지만,
 * 서비스의 {@code isOwnedBy} 2차 방어가 그대로 살아 있어 남의 테넌트 데이터가 나가지는 않는다.
 * 인가 자체는 {@code SecurityConfig} 가 이미 걸렀다.
 */
@Component
public class TenantScopeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantScopeFilter.class);

    /** 기업 담당자 전용 영역. 이 접두어 아래에서만 테넌트 필터가 켜진다. */
    static final String COMPANY_AREA = "/api/v1/companies/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean opened = openScopeIfCompanyArea(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (opened) {
                TenantScope.clear();
            }
        }
    }

    private boolean openScopeIfCompanyArea(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(request.getContextPath() + COMPANY_AREA)) {
            return false;
        }
        try {
            TenantScope.set(TenantContext.companyId());
            return true;
        } catch (TenantRequiredException e) {
            // 기업 영역인데 기업 토큰이 아니다. 인가 규칙이 곧 403 으로 끊을 요청이므로 여기서는
            // 스코프만 열지 않고 넘긴다. (인증 실패 요청도 이 경로로 들어온다)
            log.debug("기업 영역 요청이지만 테넌트를 확정할 수 없어 스코프를 열지 않는다. uri={}, 사유={}",
                    request.getRequestURI(), e.getMessage());
            return false;
        }
    }
}
