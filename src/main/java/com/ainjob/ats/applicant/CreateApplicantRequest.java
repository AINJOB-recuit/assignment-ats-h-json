package com.ainjob.ats.applicant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 지원자 등록(= 구직자 회원가입) 요청 — 계정 + 프로필 + 학력 + 경력 + 경력별 스킬을
 * <b>한 번에</b> 받는다.
 *
 * <p>학력·경력을 별도 API 로 쪼개지 않은 이유: 합격자 필터는 학력·경력·스킬이 <b>모두</b> 있어야
 * 판정할 수 있다. 프로필만 먼저 만들어지는 중간 상태를 허용하면 "스펙이 비어 있어서 탈락"인
 * 지원자가 생긴다.
 *
 * <p>company_id 가 없는 것에 주목. 지원자는 글로벌 풀이라 어느 기업에도 귀속되지 않는다.
 *
 * @param gender 0/1 로 저장되는 스키마를 그대로 따른다. 선택 항목이다.
 */
public record CreateApplicantRequest(
        @NotBlank(message = "name은 필수입니다.")
        @Size(max = 50, message = "name은 50자 이하여야 합니다.")
        String name,

        @NotBlank(message = "email은 필수입니다.")
        @Email(message = "email 형식이 올바르지 않습니다.")
        @Size(max = 120, message = "email은 120자 이하여야 합니다.")
        String email,

        // bcrypt 는 72바이트를 넘는 입력을 조용히 잘라 낸다. 잘린 뒤에도 로그인은 되지만
        // "설정한 것과 다른 비밀번호로도 로그인된다"는 상태가 되므로 여기서 길이를 제한한다.
        @NotBlank(message = "password는 필수입니다.")
        @Size(min = 8, max = 72, message = "password는 8자 이상 72자 이하여야 합니다.")
        String password,

        LocalDate birthDate,

        Boolean gender,

        @Valid List<EducationItem> educations,

        @Valid List<CareerItem> careers) {

    /** 학력 1건. 지원자당 학위별 1건이다(uq_education). */
    public record EducationItem(
            @NotBlank(message = "educations[].degreeCode는 필수입니다. (예: BACHELOR)")
            String degreeCode,

            @NotBlank(message = "educations[].majorName은 필수입니다.")
            String majorName,

            @NotBlank(message = "educations[].schoolName은 필수입니다.")
            @Size(max = 100, message = "educations[].schoolName은 100자 이하여야 합니다.")
            String schoolName) {
    }

    /**
     * 경력 1건.
     *
     * @param endAt      비우면 재직 중으로 본다
     * @param skillCodes 이 경력에서 사용한 스킬. 공고 필수스킬 판정의 원천이다
     */
    public record CareerItem(
            @NotBlank(message = "careers[].positionCode는 필수입니다. (예: BE, FE)")
            String positionCode,

            @NotBlank(message = "careers[].companyName은 필수입니다.")
            @Size(max = 100, message = "careers[].companyName은 100자 이하여야 합니다.")
            String companyName,

            // 미래 날짜를 막지 않는다. 입사 예정이거나 퇴사일이 정해진 경력을 미리 등록하는 것은
            // 정상적인 입력이기 때문이다. 대신 '아직 시작하지 않은 경력은 0개월'이라는 규칙을
            // 계산 시점에 적용한다 — Applicant.careerYearsOf 및 합격자 필터 쿼리 두 벌.
            @NotNull(message = "careers[].startAt은 필수입니다.")
            LocalDateTime startAt,

            LocalDateTime endAt,

            List<@NotBlank(message = "careers[].skillCodes에 빈 값은 넣을 수 없습니다.") String> skillCodes) {

        /**
         * 재직 기간이 거꾸로 뒤집힌 경력을 막는다.
         *
         * <p>DB 제약으로는 표현할 수 없는 규칙이라 여기서 잡는다. 통과시키면 경력연수 합산이
         * 음수가 되어 요구 연차 판정이 조용히 망가진다.
         *
         * <p><b>미래 날짜는 막지 않는다.</b> {@code endAt} 이 null 이면(재직 중) 비교할 대상이
         * 없어 이 검사는 그냥 통과하고, {@code startAt} 이 미래여도 막지 않는다. 그런 값이 계산에
         * 흘러들면 개월수가 음수가 될 수 있는데, 그건 <b>입력을 거부해서가 아니라 계산 규칙으로</b>
         * 막는다 — {@code Applicant.careerYearsOf} 주석 참고.
         */
        @AssertTrue(message = "careers[].endAt은 startAt 이후여야 합니다.")
        public boolean isPeriodValid() {
            return startAt == null || endAt == null || !endAt.isBefore(startAt);
        }
    }
}
