package com.ainjob.ats.tenant;

/**
 * "이 요청은 기업 테넌트 범위에서 실행된다"는 표시와 그 테넌트 식별자.
 *
 * <p>{@link TenantContext} 와 역할이 다르다. TenantContext 는 <b>토큰에서 값을 읽는</b> 접근점이고,
 * 여기는 <b>테넌트 필터를 켤지 말지</b>를 요청 단위로 들고 있는 스위치다. 둘을 나눈 이유는
 * 기업 회원이 공개 엔드포인트를 부를 때도 토큰에는 {@code company_id} 가 들어 있기 때문이다 —
 * 토큰만 보고 필터를 켜면 공개 공고 목록이 자기 회사 것만 나온다.
 *
 * <p>스코프를 켜고 끄는 곳은 {@link TenantScopeFilter} 하나뿐이고, 읽는 곳은
 * {@link TenantFilterAspect} 하나뿐이다.
 *
 * <p><b>ThreadLocal 이므로 다른 스레드로 전파되지 않는다.</b> {@code @Async} 알림 리스너처럼
 * 요청 스레드를 벗어난 코드에서는 스코프가 비어 있고, 따라서 테넌트 필터도 켜지지 않는다.
 * 그런 코드가 나중에 DB 를 조회하게 되면 격리가 적용되지 않으므로, 조회 조건을 직접 명시해야 한다.
 */
public final class TenantScope {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantScope() {
    }

    /** 기업 테넌트 범위를 연다. 반드시 {@code try/finally} 로 {@link #clear()} 와 짝지어 쓴다. */
    public static void set(long companyId) {
        CURRENT.set(companyId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 필터를 켜야 하는 요청인지. */
    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    /**
     * @return 현재 테넌트
     * @throws IllegalStateException 스코프가 열려 있지 않은 경우. 호출 전에 {@link #isActive()} 로 확인한다
     */
    public static long companyId() {
        Long companyId = CURRENT.get();
        if (companyId == null) {
            throw new IllegalStateException("테넌트 스코프가 열려 있지 않습니다.");
        }
        return companyId;
    }
}
