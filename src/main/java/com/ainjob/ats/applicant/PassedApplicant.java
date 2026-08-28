package com.ainjob.ats.applicant;

/** 합격자 필터 결과 1행. */
public record PassedApplicant(
        long applicationId,
        long applicantId,
        String applicantName,
        String email,
        String positionCode,
        int careerYears,
        short currentStageTypeId,
        String currentStageCode,
        String currentStageName) {
}
