package com.ainjob.ats.jobposting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 공고 등록 요청.
 *
 * <p><b>company_id 는 받지 않는다.</b> 공고의 소속 기업은 로그인한 담당자의 소속으로 확정된다 —
 * 다른 회사 이름으로 공고를 낼 경로가 없다.
 *
 * <p>마스터는 PK 가 아니라 코드로 받는다({@code "BE"}, {@code "JAVA"}, {@code "BACHELOR"}).
 * 클라이언트가 마스터 PK 를 알아야 하는 설계는 마스터가 재적재되는 순간 깨진다.
 *
 * @param positionCode      이 공고가 뽑는 직무 (BE / FE)
 * @param openAt            공고 시작일. 생략하면 등록 시각
 * @param requiredSkillCodes 필수스킬. 전부 갖춰야 통과한다(AND)
 * @param requiredCareers   요구 경력. 여러 직무를 넣으면 전부 충족해야 한다(AND)
 * @param requiredEducations 요구 학력. 여러 건을 넣으면 하나만 맞으면 된다(OR)
 */
public record CreateJobPostingRequest(
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 150, message = "title은 150자 이하여야 합니다.")
        String title,

        String content,

        @NotBlank(message = "positionCode는 필수입니다. (예: BE, FE)")
        String positionCode,

        LocalDateTime openAt,

        LocalDateTime closeAt,

        List<@NotBlank(message = "requiredSkillCodes에 빈 값은 넣을 수 없습니다.") String> requiredSkillCodes,

        @Valid List<RequiredCareer> requiredCareers,

        @Valid List<RequiredEducation> requiredEducations) {

    /** 직무별 최소 경력연수. */
    public record RequiredCareer(
            @NotBlank(message = "requiredCareers[].positionCode는 필수입니다.")
            String positionCode,

            @NotNull(message = "requiredCareers[].years는 필수입니다.")
            @PositiveOrZero(message = "requiredCareers[].years는 0 이상이어야 합니다.")
            Short years) {
    }

    /** (학위, 전공) 조합. */
    public record RequiredEducation(
            @NotBlank(message = "requiredEducations[].degreeCode는 필수입니다.")
            String degreeCode,

            @NotBlank(message = "requiredEducations[].majorName은 필수입니다.")
            String majorName) {
    }
}
