package com.ainjob.ats.master;

import com.ainjob.ats.domain.StageType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 채용 단계 마스터 조회.
 *
 * <p>전이 규칙(StageTransitionPolicy)이 기동 시 여기서 전체 단계를 읽어 구성된다.
 */
public interface StageTypeRepository extends JpaRepository<StageType, Short> {
}
