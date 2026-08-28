package com.ainjob.ats.tenant;

import com.ainjob.ats.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 기업(테넌트) 저장소.
 *
 * <p>company_id 는 검증된 토큰에서만 오므로, 연관을 걸 때는 존재 확인 없이
 * {@code getReferenceById} 로 프록시만 얻어 불필요한 SELECT 를 피한다.
 */
public interface CompanyRepository extends JpaRepository<Company, Long> {
}
