package com.ainjob.ats.applicant;

import java.util.List;

/**
 * 합격자 필터 응답.
 *
 * <p>어떤 테넌트/공고 기준으로 필터링됐는지 응답에 명시해, Postman 캡처만으로도
 * company_id 필터가 적용됐음을 확인할 수 있게 한다.
 */
public record PassedApplicantsResponse(
        long companyId,
        String companyName,
        long jobPostingId,
        String jobPostingTitle,
        int totalCount,
        List<PassedApplicant> items) {
}
