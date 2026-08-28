package com.ainjob.ats.master;

import com.ainjob.ats.domain.PositionType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 직무 마스터 조회 (BE / FE). */
public interface PositionTypeRepository extends JpaRepository<PositionType, Short> {

    Optional<PositionType> findByCode(String code);

    /** 요청에 실린 직무 코드를 한 번에 조회한다. */
    List<PositionType> findByCodeIn(Collection<String> codes);
}
