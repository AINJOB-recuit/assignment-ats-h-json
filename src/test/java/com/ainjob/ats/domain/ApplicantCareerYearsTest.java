package com.ainjob.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 경력연수 집계 규칙 — <b>개월수는 음수가 되지 않는다.</b>
 *
 * <p>이 규칙이 왜 테스트로 고정되어야 하는지. 합격자 필터는 구현이 둘이고(JPA HQL / 과제 원본 SQL),
 * 둘의 동등성은 <b>개월수가 음수가 아니라는 전제</b> 위에 서 있다. 자바의 {@code /} 와 SQL 의
 * {@code DIV} 는 0 방향으로 절단하는데 HQL 번역의 부등식은 floor 를 전제하므로, 음수 구간에서만
 * 두 구현의 답이 갈린다. 미래 날짜를 입력에서 막지 않기로 한 이상 그 전제를 지키는 것은
 * 전적으로 이 계산 규칙의 몫이다.
 *
 * <p>DB 가 필요 없다 — 순수 도메인 계산이다.
 */
class ApplicantCareerYearsTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 28, 0, 0);

    /** BE = 1, FE = 2 로 둔다(더미 스키마와 같다). */
    private static final short BE = 1;
    private static final short FE = 2;

    @Nested
    @DisplayName("정상 경력")
    class Normal {

        @Test
        @DisplayName("종료일이 있는 경력은 그 기간만큼 센다")
        void closedCareer() {
            Applicant applicant = applicantWith(BE, "2020-01-01T00:00", "2025-01-01T00:00");

            assertThat(applicant.careerYearsOf(BE, NOW)).isEqualTo(5);
        }

        @Test
        @DisplayName("재직 중(종료일 없음)이면 기준 시각까지 센다")
        void ongoingCareer() {
            Applicant applicant = applicantWith(BE, "2020-08-28T00:00", null);

            assertThat(applicant.careerYearsOf(BE, NOW)).isEqualTo(6);
        }

        @Test
        @DisplayName("월 단위로 합산한 뒤 12로 나눈다 — 건별로 절삭하지 않는다")
        void sumsInMonthsNotYears() {
            // 11개월 + 11개월 = 22개월 → 1년. 건별로 끊었다면 0년이 된다.
            Applicant applicant = applicantWith(BE, "2020-01-01T00:00", "2020-12-01T00:00");
            applicant.addCareer(positionType(BE), "두번째회사",
                    LocalDateTime.parse("2021-01-01T00:00"), LocalDateTime.parse("2021-12-01T00:00"));

            assertThat(applicant.careerYearsOf(BE, NOW)).isEqualTo(1);
        }

        @Test
        @DisplayName("다른 직무의 경력은 섞이지 않는다")
        void filtersByPosition() {
            Applicant applicant = applicantWith(BE, "2016-01-01T00:00", "2026-01-01T00:00");

            assertThat(applicant.careerYearsOf(FE, NOW)).isZero();
        }
    }

    @Nested
    @DisplayName("음수가 될 수 있는 경력 — 0으로 본다")
    class NeverNegative {

        @Test
        @DisplayName("아직 시작하지 않은 경력(재직 중)은 0개월이다")
        void futureStartWithoutEnd() {
            // 종료일이 없으므로 기준 시각까지로 보는데, 시작일이 그보다 뒤다 → 그냥 두면 음수.
            Applicant applicant = applicantWith(BE, "2099-01-01T00:00", null);

            assertThat(applicant.careerYearsOf(BE, NOW)).isZero();
        }

        @Test
        @DisplayName("시작일도 종료일도 미래면 0개월이다 — 기간이 양수여도 아직 일하지 않았다")
        void futureStartAndEnd() {
            Applicant applicant = applicantWith(BE, "2099-01-01T00:00", "2105-01-01T00:00");

            assertThat(applicant.careerYearsOf(BE, NOW)).isZero();
        }

        @Test
        @DisplayName("기간이 뒤집힌 경력은 0개월이다 — 등록 API 밖으로 들어온 데이터 방어")
        void reversedPeriod() {
            Applicant applicant = applicantWith(BE, "2025-01-01T00:00", "2020-01-01T00:00");

            assertThat(applicant.careerYearsOf(BE, NOW)).isZero();
        }

        @Test
        @DisplayName("미래 경력이 과거 경력을 깎아먹지 않는다 — 합산 전에 건별로 0이 된다")
        void futureCareerDoesNotReduceRealOnes() {
            Applicant applicant = applicantWith(BE, "2016-01-01T00:00", "2026-01-01T00:00");  // 10년
            applicant.addCareer(positionType(BE), "입사예정회사",
                    LocalDateTime.parse("2099-01-01T00:00"), null);

            // 합계에 -73년쯤이 더해졌다면 음수가 됐을 것이다.
            assertThat(applicant.careerYearsOf(BE, NOW)).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("미래 종료일 — 음수가 아니므로 그대로 센다")
    class FutureEnd {

        @Test
        @DisplayName("퇴사 예정일이 미래인 경력은 그 날짜까지 센다 (알려진 동작)")
        void futureEndCounts() {
            // 재직 중(종료일 없음)과 같은 취급이다. 음수가 아니므로 두 구현도 갈리지 않는다.
            // 경력이 부풀려질 수 있다는 점은 README 11-4 에 알려진 한계로 적어 두었다.
            Applicant applicant = applicantWith(BE, "2020-01-01T00:00", "2030-01-01T00:00");

            assertThat(applicant.careerYearsOf(BE, NOW)).isEqualTo(10);
        }
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static Applicant applicantWith(short positionTypeId, String startAt, String endAt) {
        Applicant applicant = new Applicant(
                "테스트", "test@example.com", "{noop}pw", null, null);
        applicant.addCareer(positionType(positionTypeId), "회사",
                LocalDateTime.parse(startAt),
                endAt == null ? null : LocalDateTime.parse(endAt));
        return applicant;
    }

    /**
     * 마스터 엔티티는 식별자만 있으면 된다 — 계산이 {@code position_type_id} 로만 거른다.
     *
     * <p>{@code PositionType} 은 식별자가 DB 에서 생성되는 엔티티라 공개 생성자가 없다. 이 테스트
     * 하나 때문에 프로덕션 코드에 생성자를 열지 않고 리플렉션으로 채운다.
     */
    private static PositionType positionType(short id) {
        PositionType positionType = new PositionType();
        ReflectionTestUtils.setField(positionType, "id", id);
        ReflectionTestUtils.setField(positionType, "code", "P" + id);
        ReflectionTestUtils.setField(positionType, "name", "직무" + id);
        return positionType;
    }
}
