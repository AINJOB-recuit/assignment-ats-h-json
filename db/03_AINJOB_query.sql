-- 목적      : 특정 회사(company_id)의 특정 공고(job_posting_id) 합격자 중
--             공고가 요구한 학력·전공 / 경력 / 필수스킬을 모두 충족하는 지원자 추출
-- 전제/가정 : 조건값은 하드코딩하지 않고 job_posting_education / _career / _skill 에서 읽는다.
--             전공은 OR(컴퓨터공학 또는 소프트웨어공학), 경력은 AND(요구 직무 전부 충족),
--             스킬은 AND(관계 나눗셈)로 결합한다.
--             경력 요건은 본 과제상 공고당 1건이나, 스키마가 (job_posting_id, position_type_id)
--             복합 UNIQUE 로 복수 등록을 허용하므로 NOT EXISTS 로 AND 결합한다.
--             경력연수는 행별 절삭 합산을 피하기 위해 월 단위로 합산 후 12로 나눈다.
--             경력 한 건의 개월수는 CASE 로 감싸 음수가 되지 않게 한다 — 아직 시작하지 않은
--             경력(start_dt > NOW(), 입사 예정)과 기간이 뒤집힌 경력을 0개월로 본다.
--             DIV 는 0 방향으로 절단하므로 음수 개월수는 판정 결과를 뒤집을 수 있고,
--             같은 쿼리를 JPA(HQL)로 옮긴 구현과도 답이 갈린다(부등식 번역은 floor 전제).
--             퇴사 예정(end_dt 가 미래)은 재직 중과 같이 그대로 센다 — 음수가 아니다.
-- 인덱스 전제: application (company_id, job_posting_id, applicant_id) UNIQUE  ← 테넌트 격리 겸용
--             career (applicant_id, position_type_id) / career_skill (career_id, skill_id) UNIQUE
--             education (applicant_id, degree_level_id) UNIQUE
SET @company_id = 1;
SET @job_posting_id = 1;

SELECT ap.application_id,
       co.name  AS company,
       jp.title AS job_posting,
       pt.code  AS position,
       al.name  AS applicant_name,
       al.email,
       (SELECT COALESCE(SUM(
                 CASE WHEN c.start_dt > NOW() OR COALESCE(c.end_dt, NOW()) < c.start_dt THEN 0
                      ELSE TIMESTAMPDIFF(MONTH, c.start_dt, COALESCE(c.end_dt, NOW())) END), 0) DIV 12
          FROM career c
         WHERE c.applicant_id = ap.applicant_id
           AND c.position_type_id = jp.position_type_id) AS career_years,
       cst.name AS current_stage
  FROM application ap
  JOIN company co  ON co.company_id = ap.company_id
  JOIN job_posting jp ON jp.job_posting_id = ap.job_posting_id
  JOIN position_type pt ON pt.position_type_id = jp.position_type_id
  JOIN applicant al ON al.applicant_id = ap.applicant_id
  JOIN stage_type cst ON cst.stage_type_id = ap.stage_type_id
                      AND cst.is_passed = 1
 WHERE ap.company_id = @company_id
   AND ap.job_posting_id = @job_posting_id

   AND EXISTS (
               SELECT 1
                 FROM job_posting_education jpe
                 JOIN education e ON e.degree_level_id = jpe.degree_level_id
                                 AND e.major_id = jpe.major_id
                WHERE jpe.job_posting_id = jp.job_posting_id
                  AND e.applicant_id = ap.applicant_id
              )
   -- 공고가 요구한 직무 경력 중 '미달인 요건이 하나도 없어야' 통과 (AND 결합)
   AND NOT EXISTS (
                   SELECT 1
                     FROM job_posting_career jpc
                    WHERE jpc.job_posting_id = jp.job_posting_id
                      AND (SELECT COALESCE(SUM(
                                     CASE WHEN c.start_dt > NOW() OR COALESCE(c.end_dt, NOW()) < c.start_dt THEN 0
                                          ELSE TIMESTAMPDIFF(MONTH, c.start_dt, COALESCE(c.end_dt, NOW())) END), 0) DIV 12
                             FROM career c
                            WHERE c.applicant_id = ap.applicant_id
                              AND c.position_type_id = jpc.position_type_id) < jpc.career_years
                  )
   AND (
        SELECT COUNT(DISTINCT cs.skill_id)
          FROM career c
          JOIN career_skill cs ON cs.career_id = c.career_id
         WHERE c.applicant_id = ap.applicant_id
           AND cs.skill_id IN (
                               SELECT jps.skill_id
                                 FROM job_posting_skill jps
                                WHERE jps.job_posting_id = jp.job_posting_id
                              )
       )
       =
       (
        SELECT COUNT(*)
          FROM job_posting_skill jps
         WHERE jps.job_posting_id = jp.job_posting_id
       )
ORDER BY ap.application_id;
