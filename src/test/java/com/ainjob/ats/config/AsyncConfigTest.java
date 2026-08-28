package com.ainjob.ats.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainjob.ats.notification.StageChangedEmailListener;
import com.ainjob.ats.notification.StageChangedEvent;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 알림 실행기가 <b>부트 기본 실행기와 별개로</b> 존재하는지 고정한다.
 *
 * <p>이 테스트가 있는 이유. 예전 {@code AsyncConfig} 는 실행기를
 * {@code applicationTaskExecutor} 라는 이름으로 등록해 부트 기본 실행기 자리를 차지했다.
 * 그러면 한정자 없는 {@code @Async} 와 Spring MVC 비동기 요청 처리가 알림 풀(최대 5 스레드)을
 * 함께 쓴다. 겉으로는 아무 증상이 없어서 <b>테스트로 고정해 두지 않으면 조용히 되돌아가는</b>
 * 종류의 결정이다.
 *
 * <p>DB 가 필요 없다. {@link ApplicationContextRunner} 로 이 설정과 부트의 실행기 자동설정만
 * 올려서 빈 배선만 확인한다.
 */
class AsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(AsyncConfig.class);

    @Test
    @DisplayName("알림 풀과 부트 기본 실행기가 서로 다른 빈으로 공존한다")
    void notifyExecutorDoesNotReplaceBootDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasBean(AsyncConfig.NOTIFY_EXECUTOR);

            // 부트 기본 실행기가 살아 있어야 한다. 이게 없으면 MVC 비동기 요청 처리가
            // 알림 풀로 넘어가거나(같은 이름일 때) 실행기 없이 돌아간다.
            assertThat(context).hasBean("applicationTaskExecutor");

            assertThat(context.getBean(AsyncConfig.NOTIFY_EXECUTOR))
                    .isNotSameAs(context.getBean("applicationTaskExecutor"));
        });
    }

    @Test
    @DisplayName("알림 풀의 스레드 이름·용량 설정이 유지된다")
    void notifyExecutorIsConfigured() {
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor =
                    context.getBean(AsyncConfig.NOTIFY_EXECUTOR, ThreadPoolTaskExecutor.class);

            assertThat(executor.getThreadNamePrefix()).isEqualTo("ats-notify-");
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("범용 실행기는 taskExecutor 별칭도 갖는다 — 한정자 없는 @Async 의 기본 선택")
    void generalExecutorKeepsBothNames() {
        contextRunner.run(context -> {
            // 실행기가 둘이면 타입만으로는 유일하게 정해지지 않는다. 이 별칭이 있어야
            // 한정자 없는 @Async 가 알림 풀이 아니라 범용 풀로 간다.
            assertThat(context).hasBean("taskExecutor");
            assertThat(context.getBean("taskExecutor"))
                    .isSameAs(context.getBean("applicationTaskExecutor"));
        });
    }

    @Test
    @DisplayName("범용 실행기는 MVC 비동기가 쓸 수 있는 타입이어야 한다")
    void bootDefaultRemainsUsableByMvc() {
        // WebMvcAutoConfiguration 은 'applicationTaskExecutor' 가 AsyncTaskExecutor 일 때만
        // MVC 비동기 실행기로 채택한다. 우리 변경이 그 조건을 깨지 않았는지 본다.
        contextRunner.run(context ->
                assertThat(context.getBean("applicationTaskExecutor"))
                        .isInstanceOf(AsyncTaskExecutor.class));
    }

    @Test
    @DisplayName("알림 리스너는 한정자로 알림 풀을 지정한다 — 비우면 기본 풀로 샌다")
    void listenerTargetsTheNotifyExecutor() throws Exception {
        Method listener = StageChangedEmailListener.class
                .getDeclaredMethod("on", StageChangedEvent.class);

        Async async = listener.getAnnotation(Async.class);
        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo(AsyncConfig.NOTIFY_EXECUTOR);
    }
}
