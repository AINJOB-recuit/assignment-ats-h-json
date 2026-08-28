package com.ainjob.ats.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 요구사항 3 — 상태 변경 알림 메일 본문 검증. */
class StageChangedEmailListenerTest {

    private final List<EmailMessage> sent = new ArrayList<>();
    private final StageChangedEmailListener listener = new StageChangedEmailListener(sent::add);

    @Test
    @DisplayName("상태 변경 이벤트를 받으면 지원자에게 변경 전/후 단계를 담은 메일을 보낸다")
    void sendsMailOnStageChanged() {
        listener.on(event("면접", "최종합격", "최종 합격 안내"));

        assertThat(sent).hasSize(1);
        EmailMessage message = sent.get(0);
        assertThat(message.to()).isEqualTo("h.json248@gmail.com");
        assertThat(message.subject()).contains("기업1", "최종합격");
        assertThat(message.body())
                .contains("문지후")
                .contains("변경 전  : 면접")
                .contains("변경 후  : 최종합격")
                .contains("최종 합격 안내");
    }

    @Test
    @DisplayName("사유가 없으면 안내 줄을 넣지 않는다")
    void omitsReasonLineWhenBlank() {
        listener.on(event("서류접수", "면접", null));

        assertThat(sent.get(0).body()).doesNotContain("안내");
    }

    @Test
    @DisplayName("메일 발송이 실패해도 예외를 밖으로 던지지 않는다 (전이 결과에 영향 없음)")
    void swallowsSendFailure() {
        StageChangedEmailListener failing = new StageChangedEmailListener(message -> {
            throw new IllegalStateException("SMTP down");
        });

        failing.on(event("면접", "최종합격", null)); // 예외가 전파되지 않아야 한다
    }

    private StageChangedEvent event(String from, String to, String reason) {
        return new StageChangedEvent(3L, 1L, "기업1", "기업1 백엔드 개발자 채용",
                "문지후", "h.json248@gmail.com", from, to, reason, "recruiter@company1.com",
                OffsetDateTime.of(2026, 6, 13, 10, 0, 0, 0, ZoneOffset.ofHours(9)));
    }
}
