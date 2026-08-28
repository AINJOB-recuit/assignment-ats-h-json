package com.ainjob.ats.stage;

import com.ainjob.ats.domain.StageType;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * ATS 상태 전이 규칙 — 전이표는 {@code stage_type} 마스터에서 조립한다({@link com.ainjob.ats.config.StageConfig}).
 *
 * <pre>
 *  APPLIED --FORWARD--> INTERVIEW --FORWARD--> HIRED
 *     |                     |
 *     +------REJECT---------+-----> REJECTED
 *  HIRED  --CANCEL--> 직전 단계        (종결 탈출은 CANCEL만 허용)
 *  REJECTED --CANCEL--> 직전 단계
 * </pre>
 *
 * <p>단계 목록은 stage_type 마스터에서 주입받는다. 단계가 추가돼도(예: 코딩테스트)
 * 이 클래스는 수정할 필요가 없다.
 */
public class StageTransitionPolicy {

    /** 진행 라인: 종결이 아니거나(진행 중) 합격 종결인 단계를 sort_order 순으로. → 서류접수 → 면접 → 최종합격 */
    private final List<StageType> progression;

    /** 탈락 단계: 종결이면서 합격이 아닌 단계. */
    private final StageType rejected;

    public StageTransitionPolicy(List<StageType> stageTypes) {
        this.progression = stageTypes.stream()
                .filter(s -> !s.isTerminal() || s.isPassed())
                .sorted(Comparator.comparingInt(StageType::getSortOrder))
                .toList();
        this.rejected = stageTypes.stream()
                .filter(s -> s.isTerminal() && !s.isPassed())
                .min(Comparator.comparingInt(StageType::getSortOrder))
                .orElseThrow(() -> new IllegalStateException("stage_type 마스터에 탈락 단계(is_terminal=1, is_passed=0)가 없습니다."));
        if (progression.size() < 2) {
            throw new IllegalStateException("stage_type 마스터의 진행 단계가 2개 미만입니다.");
        }
    }

    /**
     * 지원 접수 시 부여할 첫 단계 (= sort_order 가 가장 작은 진행 단계, 서류접수).
     *
     * <p>접수 로직이 {@code stage_type_id = 1} 을 직접 쓰지 않게 하기 위한 것이다. 마스터에 단계가
     * 추가되거나 순서가 바뀌어도 접수 코드는 그대로다.
     */
    public StageType initialStage() {
        return progression.get(0);
    }

    /**
     * 전이 결과 단계를 계산한다.
     *
     * @param current       현재 단계
     * @param transition    전이 종류
     * @param previousStage CANCEL 시 stage 이력에서 역산한 직전 단계 (그 외에는 무시)
     * @throws InvalidStageTransitionException 전이표에 없는 전이
     */
    public StageType resolveTarget(StageType current, TransitionType transition, StageType previousStage) {
        return switch (transition) {
            case FORWARD -> forward(current);
            case REJECT -> reject(current);
            case CANCEL -> cancel(current, previousStage);
        };
    }

    private StageType forward(StageType current) {
        if (current.isTerminal()) {
            throw new InvalidStageTransitionException(
                    current.getName() + "은(는) 종결 상태이므로 진행할 수 없습니다. 되돌리려면 CANCEL을 사용하세요.",
                    current.getId(), null);
        }
        int index = indexInProgression(current);
        if (index < 0 || index + 1 >= progression.size()) {
            throw new InvalidStageTransitionException(
                    current.getName() + " 다음 진행 단계가 없습니다.", current.getId(), null);
        }
        return progression.get(index + 1);
    }

    private StageType reject(StageType current) {
        if (current.isTerminal()) {
            throw new InvalidStageTransitionException(
                    current.getName() + "은(는) 종결 상태이므로 탈락 처리할 수 없습니다.", current.getId(), rejected.getId());
        }
        return rejected;
    }

    private StageType cancel(StageType current, StageType previousStage) {
        if (!current.isTerminal()) {
            throw new InvalidStageTransitionException(
                    "CANCEL은 종결 상태(최종합격/불합격) 탈출 전용입니다. 현재 단계=" + current.getName(),
                    current.getId(), null);
        }
        if (previousStage == null) {
            throw new InvalidStageTransitionException(
                    "복구할 직전 단계 이력이 없어 취소할 수 없습니다.", current.getId(), null);
        }
        if (previousStage.isTerminal()) {
            throw new InvalidStageTransitionException(
                    "직전 단계(" + previousStage.getName() + ")도 종결 상태여서 복구 대상이 될 수 없습니다.",
                    current.getId(), previousStage.getId());
        }
        return previousStage;
    }

    /** 요청 본문의 toStageTypeId가 서버 계산 결과와 다르면 409. CANCEL처럼 서버가 역산하는 경우 생략 가능. */
    public void verifyRequestedTarget(StageType current, StageType computed, Short requestedStageTypeId) {
        if (requestedStageTypeId == null) {
            return;
        }
        if (requestedStageTypeId != computed.getId()) {
            throw new InvalidStageTransitionException(
                    current.getName() + "에서 요청한 단계(stage_type_id=" + requestedStageTypeId + ")로는 전이할 수 없습니다."
                            + " 허용 단계=" + computed.getName() + "(stage_type_id=" + computed.getId() + ")",
                    current.getId(), requestedStageTypeId);
        }
    }

    private int indexInProgression(StageType stage) {
        for (int i = 0; i < progression.size(); i++) {
            if (progression.get(i).getId() == stage.getId()) {
                return i;
            }
        }
        return -1;
    }

    public Optional<StageType> findById(short stageTypeId) {
        return progression.stream()
                .filter(s -> s.getId() == stageTypeId)
                .findFirst()
                .or(() -> rejected.getId() == stageTypeId ? Optional.of(rejected) : Optional.empty());
    }
}
