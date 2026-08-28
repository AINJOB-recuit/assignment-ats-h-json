package com.ainjob.ats.common;

import java.util.Collection;

/**
 * 요청이 마스터에 없는 코드를 지정한 경우 → 400.
 *
 * <p>클라이언트가 보낸 값이 잘못된 것이므로 404(리소스 없음)가 아니라 400(요청 오류)이다.
 */
public class MasterCodeNotFoundException extends RuntimeException {

    public MasterCodeNotFoundException(String master, Collection<String> codes) {
        super(master + " 마스터에 없는 코드입니다: " + String.join(", ", codes));
    }

    public MasterCodeNotFoundException(String master, String code) {
        super(master + " 마스터에 없는 코드입니다: " + code);
    }
}
