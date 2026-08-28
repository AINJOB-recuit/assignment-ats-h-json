package com.ainjob.ats.tenant;

/**
 * 다른 테넌트의 리소스에 접근한 경우 — <b>2차 방어선이 발동했다는 신호</b>다.
 *
 * <p><b>클라이언트에게는 404 로 나간다.</b> 403 으로 답하면 "그 번호의 리소스는 존재하지만 네 것이
 * 아니다"를 알려 주는 셈이라, 식별자를 훑어 남의 회사 공고·지원 건의 <i>존재 여부</i>를 수집할 수
 * 있다. 없는 것과 남의 것을 구분할 수 없게 만든다.
 *
 * <p><b>서버에게는 버그 신호다.</b> 정상 경로라면 {@link TenantFilterAspect} 가 켠 테넌트 필터가
 * 이미 그 행을 걸러 내서 조회 결과가 비어 있어야 하고, 따라서 여기까지 오지 않는다. 이 예외가
 * 실제로 던져졌다면 필터가 적용되지 않는 경로(PK 직접 조회 · 네이티브 SQL · 요청 스레드 밖)로
 * 데이터를 읽었다는 뜻이므로, {@code GlobalExceptionHandler} 가 ERROR 로 남긴다.
 *
 * <p>그래서 이 예외 타입을 없애지 않고 남겨 둔다 — 응답은 404 로 똑같아도, 로그에서는
 * "그냥 없는 리소스"와 "격리가 뚫릴 뻔한 접근"을 구분할 수 있어야 한다.
 */
public class CrossTenantAccessException extends RuntimeException {

    public CrossTenantAccessException(String resource, long resourceId, long companyId) {
        super(resource + "(" + resourceId + ")은(는) company_id=" + companyId + " 소유가 아닙니다.");
    }
}
