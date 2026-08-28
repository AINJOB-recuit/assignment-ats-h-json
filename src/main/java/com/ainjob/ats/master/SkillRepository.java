package com.ainjob.ats.master;

import com.ainjob.ats.domain.Skill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 스킬 마스터 조회. 비즈니스키는 code 다. */
public interface SkillRepository extends JpaRepository<Skill, Integer> {

    Optional<Skill> findByCode(String code);

    /** 등록 요청에 실린 스킬 코드를 한 번에 조회한다(코드 개수만큼 쿼리를 날리지 않는다). */
    List<Skill> findByCodeIn(Collection<String> codes);
}
