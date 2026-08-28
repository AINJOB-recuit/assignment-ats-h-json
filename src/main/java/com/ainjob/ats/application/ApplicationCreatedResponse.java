package com.ainjob.ats.application;

import com.ainjob.ats.domain.Application;
import com.ainjob.ats.domain.Stage;
import java.time.LocalDateTime;

/**
 * 지원 접수 응답.
 *
 * <p>현재 단계와 <b>그 단계가 기록된 이력 행 번호({@code stageId})</b>를 함께 내려준다.
 * 접수 시점에 이력이 실제로 쌓였다는 사실을 응답만 보고도 확인할 수 있게 하기 위해서다.
 *
 * <p>{@code createdBy} 는 지원자 본인의 이메일이다. 첫 단계를 만든 행위자가 곧 지원자이며,
 * 그래서 {@code stage.created_by}(담당자 FK)는 이 행에서 null 이다.
 */
public record ApplicationCreatedResponse(
        long applicationId,
        long companyId,
        long jobPostingId,
        String jobPostingTitle,
        long applicantId,
        String applicantName,
        short stageTypeId,
        String stageCode,
        String stageName,
        long stageId,
        String createdBy,
        LocalDateTime createdAt) {

    /** 영속성 컨텍스트가 살아 있는 트랜잭션 안에서 호출해야 한다(지연 로딩 발생). */
    public static ApplicationCreatedResponse of(Application application, Stage initialStage,
                                                String applicantEmail) {
        return new ApplicationCreatedResponse(
                application.getId(),
                application.getCompanyId(),
                application.getJobPosting().getId(),
                application.getJobPosting().getTitle(),
                application.getApplicant().getId(),
                application.getApplicant().getName(),
                application.getCurrentStage().getId(),
                application.getCurrentStage().getCode(),
                application.getCurrentStage().getName(),
                initialStage.getId(),
                applicantEmail,
                application.getCreatedAt());
    }
}
