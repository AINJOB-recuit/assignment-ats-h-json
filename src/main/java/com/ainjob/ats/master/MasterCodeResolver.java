package com.ainjob.ats.master;

import com.ainjob.ats.common.MasterCodeNotFoundException;
import com.ainjob.ats.domain.DegreeLevel;
import com.ainjob.ats.domain.Major;
import com.ainjob.ats.domain.PositionType;
import com.ainjob.ats.domain.Skill;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요청에 실린 <b>비즈니스 코드</b>를 마스터 엔티티로 바꿔준다.
 *
 * <p>API 는 마스터 PK 를 받지 않는다. 클라이언트가 {@code skill_id=7} 을 알아야 하는 설계는
 * 마스터 행이 재적재되는 순간 깨지기 때문이다. 대신 {@code "JAVA"} 처럼 안정적인 코드를 받고
 * 여기서 PK 로 바꾼다. 없는 코드는 400 으로 되돌려준다.
 *
 * <p>지원자 등록과 공고 등록이 같은 변환을 필요로 하므로 한 곳에 모았다. 이 클래스가 만드는 경계는
 * "코드→엔티티 변환은 여기 한 곳"이며, 그 덕에 어느 API 로 들어오든 없는 코드의 응답이 같다.
 *
 * <h2>네 종류 모두 한 번의 IN 조회로 바꾼다</h2>
 *
 * <p>예전에는 {@code skills()} 만 묶고 학위·전공·직무는 항목마다 조회했다. <b>성능보다 오류
 * 메시지가 문제였다</b> — 스킬 코드를 세 개 틀리면 세 개를 한 번에 알려주는데, 전공명을 세 개
 * 틀리면 하나씩 세 번 왕복해야 고칠 수 있었다. 같은 요청 안에서 필드에 따라 동작이 갈렸다.
 *
 * <p>지금은 넷 다 {@link #resolve} 하나를 쓴다. 조회가 한 번으로 줄어드는 것은 덤이다 —
 * 학력 3건 + 경력 4건짜리 회원가입이 11회에서 4회가 된다. 마스터 테이블이 작아 실측 차이는
 * 크지 않지만, 쿼리 수가 요청 항목 수에 비례하지 않게 되는 것 자체가 목적이다.
 */
@Service
@Transactional(readOnly = true)
public class MasterCodeResolver {

    private final SkillRepository skillRepository;
    private final MajorRepository majorRepository;
    private final DegreeLevelRepository degreeLevelRepository;
    private final PositionTypeRepository positionTypeRepository;

    public MasterCodeResolver(SkillRepository skillRepository,
                              MajorRepository majorRepository,
                              DegreeLevelRepository degreeLevelRepository,
                              PositionTypeRepository positionTypeRepository) {
        this.skillRepository = skillRepository;
        this.majorRepository = majorRepository;
        this.degreeLevelRepository = degreeLevelRepository;
        this.positionTypeRepository = positionTypeRepository;
    }

    /** 스킬 코드 여러 개를 한 번의 IN 조회로 바꾼다. */
    public Map<String, Skill> skills(Collection<String> codes) {
        return resolve(codes, skillRepository::findByCodeIn, Skill::getCode, "skill");
    }

    /** 학위 코드 여러 개를 한 번의 IN 조회로 바꾼다. */
    public Map<String, DegreeLevel> degreeLevels(Collection<String> codes) {
        return resolve(codes, degreeLevelRepository::findByCodeIn, DegreeLevel::getCode, "degree_level");
    }

    /** 전공명 여러 개를 한 번의 IN 조회로 바꾼다. <b>전공만 코드가 아니라 이름이 키다</b>(uq_major_name). */
    public Map<String, Major> majors(Collection<String> names) {
        return resolve(names, majorRepository::findByNameIn, Major::getName, "major");
    }

    /** 직무 코드 여러 개를 한 번의 IN 조회로 바꾼다. */
    public Map<String, PositionType> positionTypes(Collection<String> codes) {
        return resolve(codes, positionTypeRepository::findByCodeIn, PositionType::getCode, "position_type");
    }

    /**
     * 코드 하나짜리 편의 메서드 — 공고의 {@code positionCode} 처럼 본래 단일인 필드용이다.
     *
     * <p>반복문 안에서 부르지 말 것. 그러면 이 클래스가 없애려는 "항목마다 한 번씩" 이 되살아난다.
     */
    public PositionType positionType(String code) {
        return positionTypes(List.of(code)).get(code);
    }

    /**
     * 키 목록 → 마스터 엔티티 Map. 네 종류가 같은 로직이라 하나로 모았다.
     *
     * <p><b>없는 키는 전부 모아 한 번에 알린다.</b> 하나씩 튕겨내면 클라이언트가 오타를 고치느라
     * 왕복을 반복하게 된다. 중복은 {@link LinkedHashSet} 이 걸러 내고, 순서를 보존하므로 오류
     * 메시지에 나오는 코드 순서가 요청 순서와 같다.
     *
     * @param keys      요청에 실린 코드(전공은 이름). 비어 있으면 조회하지 않는다
     * @param finder    IN 조회 — {@code findByCodeIn} / {@code findByNameIn}
     * @param keyOf     조회 결과에서 키를 꺼내는 함수. {@code finder} 와 짝이 맞아야 한다
     * @param masterName 오류 메시지에 실릴 마스터 이름
     * @throws MasterCodeNotFoundException 마스터에 없는 키가 하나라도 있으면 (400)
     */
    private <T> Map<String, T> resolve(Collection<String> keys,
                                       Function<Set<String>, List<T>> finder,
                                       Function<T, String> keyOf,
                                       String masterName) {
        Set<String> requested = new LinkedHashSet<>(keys);
        if (requested.isEmpty()) {
            return Map.of();
        }

        Map<String, T> byKey = finder.apply(requested).stream()
                .collect(Collectors.toMap(keyOf, Function.identity()));

        Set<String> missing = new LinkedHashSet<>(requested);
        missing.removeAll(byKey.keySet());
        if (!missing.isEmpty()) {
            throw new MasterCodeNotFoundException(masterName, missing);
        }
        return byKey;
    }
}
