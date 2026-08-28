package com.ainjob.ats.auth;

/**
 * 구직자 전용 경로에 구직자 토큰이 아닌 요청이 도달했을 때.
 *
 * <p>정상 경로에서는 인가 규칙이 먼저 403 으로 끊으므로 여기까지 오지 않는다. 즉 이 예외가 뜨면
 * 인가 규칙과 컨트롤러가 어긋났다는 신호다 — 그래서 조용히 넘기지 않고 403 으로 드러낸다.
 */
public class NotApplicantException extends RuntimeException {

    public NotApplicantException(String message) {
        super(message);
    }
}
