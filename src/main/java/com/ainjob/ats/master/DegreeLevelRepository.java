package com.ainjob.ats.master;

import com.ainjob.ats.domain.DegreeLevel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 학위 마스터 조회. */
public interface DegreeLevelRepository extends JpaRepository<DegreeLevel, Short> {

    Optional<DegreeLevel> findByCode(String code);

    /** 요청에 실린 학위 코드를 한 번에 조회한다. */
    List<DegreeLevel> findByCodeIn(Collection<String> codes);
}
