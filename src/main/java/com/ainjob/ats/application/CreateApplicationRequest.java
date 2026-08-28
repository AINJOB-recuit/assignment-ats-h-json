package com.ainjob.ats.application;

import jakarta.validation.constraints.Size;

/**
 * 지원 요청.
 *
 * <p><b>본문에 지원자 식별자가 없다.</b> 공고는 경로에, 지원자는 토큰에 있으므로 남는 것은 메모뿐이다.
 * 이것이 대리 지원을 막는 방식이다 — 검증이 아니라 <b>입력 자체를 없애는 것</b>이라, 남의 번호를
 * 넣어 볼 자리가 요청 형식에 존재하지 않는다.
 *
 * @param reason 초기 단계 이력({@code stage.content})에 남길 자기소개·지원 사유. 선택 항목이다
 */
public record CreateApplicationRequest(

        @Size(max = 1000, message = "reason은 1000자 이하여야 합니다.")
        String reason) {
}
