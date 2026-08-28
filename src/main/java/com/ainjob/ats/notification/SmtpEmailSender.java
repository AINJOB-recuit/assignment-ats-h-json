package com.ainjob.ats.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP 발송 구현체. {@code ainjob.mail.enabled=true} 인 경우에만 등록된다.
 *
 * <p><b>발신자는 SMTP 인증 계정({@code spring.mail.username})을 그대로 쓴다.</b> 별도의 발신 주소를
 * 설정으로 두지 않는 이유는, 인증 계정과 다른 From 을 실으면 메일 서버가 이를 사칭으로 보고
 * 거부하거나(Gmail 은 553) 조용히 인증 계정 주소로 바꿔 버리기 때문이다. 설정할 수 있게 열어 두면
 * "설정한 값과 실제 발신자가 다른" 상태가 생긴다.
 */
@Component
@ConditionalOnProperty(prefix = "ainjob.mail", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.from = mailProperties.getUsername();
        // 발송 시점이 아니라 기동 시점에 막는다. 매번 발송에 실패하고 로그만 쌓이는 것보다 낫다.
        if (this.from == null || this.from.isBlank()) {
            throw new IllegalStateException(
                    "ainjob.mail.enabled=true 이면 spring.mail.username 이 필요합니다. "
                            + "이 값이 곧 발신자 주소가 됩니다.");
        }
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
