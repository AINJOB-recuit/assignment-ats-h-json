package com.ainjob.ats.config;

import com.ainjob.ats.master.StageTypeRepository;
import com.ainjob.ats.stage.StageTransitionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 상태 전이 규칙을 stage_type 마스터로부터 구성한다.
 *
 * <p>단계 이름·순서·종결 여부를 코드에 하드코딩하지 않는다("문자열 상태값 금지" 원칙과 같은 맥락).
 *
 * <p>마스터 행은 기동 시 한 번 읽어 규칙 객체에 담는다. 채용 단계는 운영 중 바뀌지 않는 참조
 * 데이터이므로 매 요청 조회하지 않는다. 여기서 넘어온 엔티티는 준영속 상태이며 <b>판정용 값</b>으로만
 * 쓴다 — 실제 연관을 걸 때는 서비스가 영속 상태의 참조를 다시 얻는다.
 */
@Configuration
public class StageConfig {

    @Bean
    public StageTransitionPolicy stageTransitionPolicy(StageTypeRepository stageTypeRepository) {
        return new StageTransitionPolicy(stageTypeRepository.findAll());
    }
}
