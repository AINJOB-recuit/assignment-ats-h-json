package com.ainjob.ats.stage;

/** 상태 전이 종류. */
public enum TransitionType {
    /** 다음 단계로 진행. */
    FORWARD,
    /** 탈락 처리. */
    REJECT,
    /** 종결 상태(최종합격/불합격) 취소 → 직전 단계로 복구. */
    CANCEL
}
