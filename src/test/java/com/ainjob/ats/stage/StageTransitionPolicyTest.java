package com.ainjob.ats.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainjob.ats.domain.StageType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link StageTransitionPolicy} 의 전이표를 검증한다. */
class StageTransitionPolicyTest {

    private static final StageType APPLIED = new StageType((short) 1, "APPLIED", "서류접수", (short) 1, false, false);
    private static final StageType INTERVIEW = new StageType((short) 2, "INTERVIEW", "면접", (short) 2, false, false);
    private static final StageType HIRED = new StageType((short) 3, "HIRED", "최종합격", (short) 3, true, true);
    private static final StageType REJECTED = new StageType((short) 4, "REJECTED", "불합격", (short) 99, true, false);

    private final StageTransitionPolicy policy =
            new StageTransitionPolicy(List.of(APPLIED, INTERVIEW, HIRED, REJECTED));

    @Nested
    @DisplayName("FORWARD")
    class Forward {

        @Test
        @DisplayName("서류접수 → 면접, 면접 → 최종합격")
        void movesOneStep() {
            assertThat(policy.resolveTarget(APPLIED, TransitionType.FORWARD, null)).isEqualTo(INTERVIEW);
            assertThat(policy.resolveTarget(INTERVIEW, TransitionType.FORWARD, null)).isEqualTo(HIRED);
        }

        @Test
        @DisplayName("단계 건너뛰기(서류접수 → 최종합격)는 409")
        void cannotSkipStage() {
            assertThatThrownBy(() -> policy.verifyRequestedTarget(
                    APPLIED, policy.resolveTarget(APPLIED, TransitionType.FORWARD, null), HIRED.getId()))
                    .isInstanceOf(InvalidStageTransitionException.class);
        }

        @Test
        @DisplayName("종결 상태(최종합격/불합격)에서는 진행 불가")
        void cannotForwardFromTerminal() {
            assertThatThrownBy(() -> policy.resolveTarget(HIRED, TransitionType.FORWARD, null))
                    .isInstanceOf(InvalidStageTransitionException.class);
            assertThatThrownBy(() -> policy.resolveTarget(REJECTED, TransitionType.FORWARD, null))
                    .isInstanceOf(InvalidStageTransitionException.class);
        }
    }

    @Nested
    @DisplayName("REJECT")
    class Reject {

        @Test
        @DisplayName("진행 중 단계에서는 언제든 탈락 처리 가능")
        void rejectFromOngoingStage() {
            assertThat(policy.resolveTarget(APPLIED, TransitionType.REJECT, null)).isEqualTo(REJECTED);
            assertThat(policy.resolveTarget(INTERVIEW, TransitionType.REJECT, null)).isEqualTo(REJECTED);
        }

        @Test
        @DisplayName("이미 종결된 지원은 탈락 처리 불가")
        void cannotRejectTerminal() {
            assertThatThrownBy(() -> policy.resolveTarget(REJECTED, TransitionType.REJECT, null))
                    .isInstanceOf(InvalidStageTransitionException.class);
        }
    }

    @Nested
    @DisplayName("CANCEL")
    class Cancel {

        @Test
        @DisplayName("최종합격 취소 → 직전 단계(면접)로 복구")
        void cancelHired() {
            assertThat(policy.resolveTarget(HIRED, TransitionType.CANCEL, INTERVIEW)).isEqualTo(INTERVIEW);
        }

        @Test
        @DisplayName("불합격 철회 → 직전 단계로 복구")
        void cancelRejected() {
            assertThat(policy.resolveTarget(REJECTED, TransitionType.CANCEL, APPLIED)).isEqualTo(APPLIED);
        }

        @Test
        @DisplayName("진행 중 단계에서는 CANCEL 불가 (종결 탈출 전용)")
        void cannotCancelOngoing() {
            assertThatThrownBy(() -> policy.resolveTarget(APPLIED, TransitionType.CANCEL, null))
                    .isInstanceOf(InvalidStageTransitionException.class);
        }

        @Test
        @DisplayName("복구할 이력이 없으면 409")
        void cannotCancelWithoutHistory() {
            assertThatThrownBy(() -> policy.resolveTarget(HIRED, TransitionType.CANCEL, null))
                    .isInstanceOf(InvalidStageTransitionException.class);
        }
    }

    @Test
    @DisplayName("지원 접수의 첫 단계는 sort_order가 가장 작은 진행 단계다")
    void initialStageComesFromMasterData() {
        assertThat(policy.initialStage()).isEqualTo(APPLIED);
    }

    @Test
    @DisplayName("첫 단계도 마스터를 따른다 — 앞에 단계가 추가되면 접수 코드 수정 없이 바뀐다")
    void initialStageFollowsMaster() {
        // APPLIED 의 sort_order 가 1 이므로, 그보다 앞선 0 을 줘야 첫 단계가 된다.
        StageType screening = new StageType((short) 6, "SCREENING", "사전검토", (short) 0, false, false);
        StageTransitionPolicy extended =
                new StageTransitionPolicy(List.of(screening, APPLIED, INTERVIEW, HIRED, REJECTED));

        assertThat(extended.initialStage()).isEqualTo(screening);
    }

    @Test
    @DisplayName("요청 toStageTypeId가 서버 계산 결과와 같으면 통과")
    void verifyRequestedTargetPasses() {
        StageType computed = policy.resolveTarget(APPLIED, TransitionType.FORWARD, null);
        policy.verifyRequestedTarget(APPLIED, computed, INTERVIEW.getId());
        policy.verifyRequestedTarget(APPLIED, computed, null); // 생략 가능
    }

    @Test
    @DisplayName("단계 마스터가 바뀌어도(중간 단계 추가) 규칙 코드는 그대로 동작한다")
    void progressionComesFromMasterData() {
        // sort_order 를 넉넉히 띄워두면 중간 단계 추가가 데이터만으로 가능하다.
        StageType applied = new StageType((short) 1, "APPLIED", "서류접수", (short) 10, false, false);
        StageType codingTest = new StageType((short) 5, "CODING_TEST", "코딩테스트", (short) 15, false, false);
        StageType interview = new StageType((short) 2, "INTERVIEW", "면접", (short) 20, false, false);
        StageType hired = new StageType((short) 3, "HIRED", "최종합격", (short) 30, true, true);
        StageTransitionPolicy extended =
                new StageTransitionPolicy(List.of(applied, codingTest, interview, hired, REJECTED));

        assertThat(extended.resolveTarget(applied, TransitionType.FORWARD, null)).isEqualTo(codingTest);
        assertThat(extended.resolveTarget(codingTest, TransitionType.FORWARD, null)).isEqualTo(interview);
        assertThat(extended.resolveTarget(interview, TransitionType.FORWARD, null)).isEqualTo(hired);
    }
}
