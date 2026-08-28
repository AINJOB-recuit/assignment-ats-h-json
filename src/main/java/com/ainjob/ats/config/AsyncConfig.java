package com.ainjob.ats.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행기 두 벌 — <b>범용 하나, 알림 전용 하나.</b>
 *
 * <p>알림 발송을 별도 풀에 묶는 것이 목적이다. 메일 I/O 가 상태 전이 요청 스레드를 붙잡지 않아야
 * 하고, 반대로 메일 서버가 느려질 때 무관한 비동기 작업까지 밀리지도 않아야 한다.
 *
 * <h2>왜 실행기를 두 개 선언하는가</h2>
 *
 * <p>처음에는 알림 실행기 하나만 {@code applicationTaskExecutor} 라는 이름으로 등록했다. 그런데
 * 그 이름은 스프링 부트가 예약해 둔 것이라 두 가지가 딸려 왔다.
 * <ol>
 *   <li><b>한정자 없는 {@code @Async} 가 전부 알림 풀로 몰린다.</b> 나중에 통계 집계나 파일 처리에
 *       {@code @Async} 를 붙이면 메일 발송과 최대 5 스레드를 나눠 쓴다. SMTP 타임아웃이 5초라
 *       (application.yml) 메일 서버가 느려지면 무관한 작업이 함께 밀린다.</li>
 *   <li><b>Spring MVC 의 비동기 요청 처리도 그 풀을 쓴다.</b> {@code WebMvcAutoConfiguration} 이
 *       {@code applicationTaskExecutor} 라는 이름의 {@code AsyncTaskExecutor} 를 찾아 쓰기 때문이다.
 *       컨트롤러가 {@code Callable}/{@code DeferredResult}/SSE 를 반환하면 HTTP 응답 처리가
 *       메일 발송 풀에서 돈다.</li>
 * </ol>
 *
 * <p>그렇다고 알림 실행기의 이름만 바꾸면 이번에는 <b>부트 기본 실행기가 아예 사라진다.</b>
 * 부트의 실행기 자동설정은 컨텍스트에 {@code Executor} 빈이 하나라도 있으면 물러나기 때문이다
 * (조건이 "이름이 겹치는가"가 아니라 "타입이 있는가"다). 그러면 {@code applicationTaskExecutor}
 * 가 없어 MVC 비동기가 요청마다 스레드를 새로 만드는 {@code SimpleAsyncTaskExecutor} 로 떨어진다 —
 * 풀 공유보다 나쁠 수 있는 상태다.
 *
 * <p>그래서 <b>범용 실행기를 부트의 빌더로 직접 되살리고</b>, 알림 전용 풀을 그 옆에 따로 둔다.
 * 빌더를 쓰므로 {@code spring.task.execution.*} 설정은 그대로 적용된다.
 *
 * <pre>
 *   applicationTaskExecutor (= taskExecutor)   범용. MVC 비동기 · 한정자 없는 @Async
 *   atsNotifyExecutor                          알림 전용. @Async("atsNotifyExecutor") 만
 * </pre>
 *
 * <p>이 배선은 겉으로 아무 증상이 없어서 조용히 되돌아가기 쉽다. {@code AsyncConfigTest} 가
 * 두 빈의 공존을 회귀 테스트로 고정한다.
 *
 * <p>{@code @EnableAsync} 는 {@code AtsApiApplication} 에 있다.
 */
@Configuration
public class AsyncConfig {

    /** 알림 실행기의 빈 이름. {@code @Async} 한정자와 이 상수가 같은 값을 가리켜야 한다. */
    public static final String NOTIFY_EXECUTOR = "atsNotifyExecutor";

    /**
     * 범용 실행기 — 부트 기본값을 그대로 되살린 것이다.
     *
     * <p>아래 알림 실행기 때문에 부트 자동설정이 물러나므로 여기서 명시적으로 등록한다.
     * 빌더를 쓰기 때문에 {@code spring.task.execution.*} 프로퍼티가 그대로 반영된다.
     *
     * <p>이름을 둘 다 붙이는 것도 부트와 같다 — {@code applicationTaskExecutor} 는 MVC 비동기가
     * 찾는 이름이고, {@code taskExecutor} 는 한정자 없는 {@code @Async} 가 실행기를 못 고를 때
     * 마지막으로 찾는 이름이다. 실행기가 둘이면 타입만으로는 유일하게 정해지지 않으므로
     * 이 별칭이 있어야 기본 선택이 확정된다.
     */
    @Bean({TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
           AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME})
    ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * 알림 전용 풀.
     *
     * <p>{@code initialize()} 를 직접 부르지 않는다. {@link ThreadPoolTaskExecutor} 는
     * {@code InitializingBean} 이라 스프링이 {@code afterPropertiesSet()} 에서 불러 주는데,
     * 여기서 한 번 더 부르면 내부 {@code ThreadPoolExecutor} 가 두 번 만들어지고 첫 번째는
     * 참조를 잃은 채 남는다({@code initialize()} 에 재진입 가드가 없다).
     */
    @Bean(NOTIFY_EXECUTOR)
    ThreadPoolTaskExecutor atsNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ats-notify-");
        // 종료 시 큐에 남은 알림을 최대 10초까지 흘려보낸다. 전이는 이미 커밋된 상태다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        // 풀도 큐(200)도 가득 차면 버린다. 기본값(AbortPolicy)은 예외를 던지는데, 그 예외가
        // 나오는 자리가 커밋 직후의 이벤트 발행 지점이라 알림 폭주가 무관한 흐름으로 새어 나간다.
        // "알림은 늦느니 포기한다"는 이 프로젝트의 방침과도 같다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
