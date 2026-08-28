package com.ainjob.ats.applicant;

import com.ainjob.ats.common.DuplicateResourceException;
import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.Career;
import com.ainjob.ats.domain.DegreeLevel;
import com.ainjob.ats.domain.Major;
import com.ainjob.ats.domain.PositionType;
import com.ainjob.ats.domain.Skill;
import com.ainjob.ats.master.MasterCodeResolver;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구직자 유스케이스 — 회원가입 / 본인 프로필 조회.
 *
 * <p>지원자는 글로벌 풀이므로 여기에는 테넌트 조건이 없다. 대신 <b>본인 여부</b>가 그 자리를 대신한다.
 * 가입은 비인증이고(가입 전에는 토큰이 없다), 조회는 토큰 주인의 것만 허용한다.
 *
 * <p>기업 담당자가 지원자를 보는 경로는 여기가 아니라 {@link CompanyApplicantService} 다 —
 * 그쪽은 "자기 공고에 지원한 사람"으로 범위가 좁혀진다.
 */
@Service
public class ApplicantService {

    private final ApplicantRepository applicantRepository;
    private final MasterCodeResolver masterCodes;
    private final PasswordEncoder passwordEncoder;

    public ApplicantService(ApplicantRepository applicantRepository,
                            MasterCodeResolver masterCodes,
                            PasswordEncoder passwordEncoder) {
        this.applicantRepository = applicantRepository;
        this.masterCodes = masterCodes;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 구직자를 계정·학력·경력·스킬까지 한 트랜잭션으로 등록한다.
     *
     * <p>부모(지원자) → 자식(학력/경력) → 손자(경력별 스킬) 3계층이 cascade 로 함께 저장된다.
     * 중간에 실패하면 전부 롤백되므로 "계정만 있고 스펙이 빈" 지원자가 남지 않는다.
     *
     * <p>평문 비밀번호는 이 메서드 밖으로 나가지 않는다 — 인코딩한 뒤 엔티티에 넘기고,
     * 응답({@link ApplicantResponse})에는 계정 필드가 아예 없다.
     */
    @Transactional
    public ApplicantResponse register(CreateApplicantRequest request, LocalDateTime now) {
        if (applicantRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException(
                    "이미 등록된 지원자 이메일입니다: " + request.email());
        }

        Applicant applicant = new Applicant(
                request.name(), request.email(), passwordEncoder.encode(request.password()),
                request.birthDate(), request.gender());

        addEducations(applicant, nullToEmpty(request.educations()));
        addCareers(applicant, nullToEmpty(request.careers()));

        return ApplicantResponse.from(applicantRepository.save(applicant), now);
    }

    /**
     * 본인 프로필 조회.
     *
     * <p><b>본인 여부를 존재 확인보다 먼저 판정한다.</b> 순서를 뒤집으면 404/403 차이로 그 번호의
     * 지원자가 실제로 있는지 알아낼 수 있게 된다 — 식별자를 1부터 훑어 가입자를 세는 경로가 열린다.
     *
     * @param applicantId          경로로 들어온 조회 대상
     * @param authenticatedId      토큰 subject 에서 나온 요청자. 클라이언트 입력이 아니다
     */
    @Transactional(readOnly = true)
    public ApplicantResponse findOwnProfile(long applicantId, long authenticatedId, LocalDateTime now) {
        if (applicantId != authenticatedId) {
            throw new NotOwnProfileException(applicantId);
        }
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new ResourceNotFoundException("applicant", applicantId));
        return ApplicantResponse.from(applicant, now);
    }

    /**
     * 학력을 붙인다. 학위는 지원자당 1건이다(uq_education) — DB 가 거부하기 전에 원인을 알려준다.
     *
     * <p>학위 코드와 전공명을 <b>각각 한 번의 IN 조회</b>로 모아 바꾼다. 항목마다 조회하면 학력
     * 건수의 두 배만큼 왕복이 생기고, 무엇보다 없는 코드를 하나씩 튕겨내게 된다.
     */
    private void addEducations(Applicant applicant, List<CreateApplicantRequest.EducationItem> items) {
        Map<String, DegreeLevel> degreeLevels = masterCodes.degreeLevels(
                items.stream().map(CreateApplicantRequest.EducationItem::degreeCode).toList());
        Map<String, Major> majors = masterCodes.majors(
                items.stream().map(CreateApplicantRequest.EducationItem::majorName).toList());

        Set<String> seenDegrees = new HashSet<>();
        for (CreateApplicantRequest.EducationItem item : items) {
            if (!seenDegrees.add(item.degreeCode())) {
                throw new DuplicateResourceException(
                        "같은 학위를 두 번 등록할 수 없습니다: " + item.degreeCode());
            }
            applicant.addEducation(
                    degreeLevels.get(item.degreeCode()),
                    majors.get(item.majorName()),
                    item.schoolName());
        }
    }

    /**
     * 경력과 그 경력에서 쓴 스킬을 붙인다.
     *
     * <p>요청 전체의 스킬 코드와 직무 코드를 <b>각각 한 번의 IN 조회</b>로 모아 바꾼다.
     * 경력마다 조회하면 경력 수만큼 왕복이 생긴다.
     */
    private void addCareers(Applicant applicant, List<CreateApplicantRequest.CareerItem> items) {
        List<String> allSkillCodes = items.stream()
                .flatMap(item -> nullToEmpty(item.skillCodes()).stream())
                .distinct()
                .toList();
        Map<String, Skill> skills = masterCodes.skills(allSkillCodes);
        Map<String, PositionType> positionTypes = masterCodes.positionTypes(
                items.stream().map(CreateApplicantRequest.CareerItem::positionCode).toList());

        for (CreateApplicantRequest.CareerItem item : items) {
            Career career = applicant.addCareer(
                    positionTypes.get(item.positionCode()),
                    item.companyName(),
                    item.startAt(),
                    item.endAt());
            // 같은 경력에 같은 스킬을 두 번 적어도 오류로 보지 않고 한 번만 반영한다
            // (uq_career_skill 위반을 사용자에게 떠넘길 이유가 없다).
            nullToEmpty(item.skillCodes()).stream()
                    .distinct()
                    .forEach(code -> career.addSkill(skills.get(code)));
        }
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
