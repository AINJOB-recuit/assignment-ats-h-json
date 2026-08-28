package com.ainjob.ats.master;

import com.ainjob.ats.domain.Major;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 전공 마스터 조회. 이 마스터만 비즈니스키가 name 이다(스키마의 uq_major_name). */
public interface MajorRepository extends JpaRepository<Major, Integer> {

    Optional<Major> findByName(String name);

    /** 요청에 실린 전공명을 한 번에 조회한다. 이 마스터만 비즈니스키가 name 이다. */
    List<Major> findByNameIn(Collection<String> names);
}
