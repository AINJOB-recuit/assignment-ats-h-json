SET NAMES utf8mb4;

DROP TABLE IF EXISTS matching;
DROP TABLE IF EXISTS stage;
DROP TABLE IF EXISTS application;
DROP TABLE IF EXISTS job_posting_education;
DROP TABLE IF EXISTS job_posting_career;
DROP TABLE IF EXISTS job_posting_skill;
DROP TABLE IF EXISTS job_posting;
DROP TABLE IF EXISTS career_skill;
DROP TABLE IF EXISTS career;
DROP TABLE IF EXISTS education;
DROP TABLE IF EXISTS applicant;
DROP TABLE IF EXISTS stage_type;
DROP TABLE IF EXISTS position_type;
DROP TABLE IF EXISTS degree_level;
DROP TABLE IF EXISTS major;
DROP TABLE IF EXISTS skill;
DROP TABLE IF EXISTS company_user;
DROP TABLE IF EXISTS company_role;
DROP TABLE IF EXISTS company;

-- ============================================================
-- 1. 마스터 / lookup (문자열 상태값 제거 근거)
-- ============================================================
CREATE TABLE company (                              -- company_id = tenant_id
  company_id  BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL,
  biz_no      VARCHAR(20)  NOT NULL,
  location    VARCHAR(255) NOT NULL,
  PRIMARY KEY (company_id),
  UNIQUE KEY uq_company_biz_no (biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기업 담당자 역할 (문자열 상태값 금지 → lookup FK)
CREATE TABLE company_role (
  role_id SMALLINT    NOT NULL AUTO_INCREMENT,
  code    VARCHAR(20) NOT NULL,                     -- OWNER / RECRUITER / VIEWER
  name    VARCHAR(50) NOT NULL,
  PRIMARY KEY (role_id),
  UNIQUE KEY uq_company_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE skill (
  skill_id INT          NOT NULL AUTO_INCREMENT,
  code     VARCHAR(20)  NOT NULL,                   -- 비즈니스키 (JAVA, AWS …)
  name     VARCHAR(100) NOT NULL,
  PRIMARY KEY (skill_id),
  UNIQUE KEY uq_skill_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 기업 소속 채용담당자 = 인증 주체
--   Tenant 도메인 책임("기업 가입·계정·인증 주체")을 담는 테이블.
--   company_id 는 요청이 아니라 이 행에서 결정된다 → 테넌트 사칭 불가.
CREATE TABLE company_user (
  company_user_id BIGINT       NOT NULL AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,            -- 멀티테넌시 격리키
  role_id         SMALLINT     NOT NULL,
  email           VARCHAR(120) NOT NULL,            -- 로그인 식별자
  password_hash   VARCHAR(100) NOT NULL,            -- {bcrypt}$2a$10$... (평문 저장 금지)
  name            VARCHAR(50)  NOT NULL,
  is_active       TINYINT(1)   NOT NULL DEFAULT 1,  -- 감사 로그 보존을 위해 하드 삭제 대신 비활성화
  created_dt      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (company_user_id),
  -- 한 이메일 = 한 기업 소속. 로그인 요청이 {email, password} 만으로 끝나고
  -- company_id 를 클라이언트가 지정할 여지가 사라진다.
  UNIQUE KEY uq_company_user_email (email),
  KEY idx_company_user_company (company_id, is_active),   -- company_id 선두 복합 인덱스
  CONSTRAINT fk_cu_company FOREIGN KEY (company_id) REFERENCES company(company_id),
  CONSTRAINT fk_cu_role    FOREIGN KEY (role_id)    REFERENCES company_role(role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE major (
  major_id INT          NOT NULL AUTO_INCREMENT,
  name     VARCHAR(100) NOT NULL,
  PRIMARY KEY (major_id),
  UNIQUE KEY uq_major_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE degree_level (
  degree_level_id SMALLINT    NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20) NOT NULL,             -- BACHELOR, MASTER …
  name            VARCHAR(20) NOT NULL,
  grade           SMALLINT    NOT NULL,             -- 등급(학사<석사<박사): '이상' 비교용
  PRIMARY KEY (degree_level_id),
  UNIQUE KEY uq_degree_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE position_type (
  position_type_id SMALLINT    NOT NULL AUTO_INCREMENT,
  code             VARCHAR(20) NOT NULL,            -- BE / FE
  name             VARCHAR(50) NOT NULL,
  PRIMARY KEY (position_type_id),
  UNIQUE KEY uq_position_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stage_type (
  stage_type_id SMALLINT    NOT NULL AUTO_INCREMENT,
  code          VARCHAR(20) NOT NULL,              -- APPLIED/INTERVIEW/HIRED/REJECTED
  name          VARCHAR(50) NOT NULL,
  sort_order    SMALLINT    NOT NULL,
  is_terminal   TINYINT(1)  NOT NULL DEFAULT 0,
  is_passed     TINYINT(1)  NOT NULL DEFAULT 0,    -- '합격'을 문자열 대신 플래그로
  PRIMARY KEY (stage_type_id),
  UNIQUE KEY uq_stage_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. 지원자(구직자 회원) 글로벌 풀 (company_id 없음 → 여러 기업 동시 지원)
-- ============================================================
-- 구직자는 이 서비스의 두 번째 로그인 주체다. company_user 와 테이블이 갈리므로
-- 한쪽 계정이 다른 쪽 권한을 얻을 경로가 스키마 수준에서 없다.
CREATE TABLE applicant (
  applicant_id  BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(50)  NOT NULL,
  email         VARCHAR(120) NOT NULL,            -- 로그인 식별자 겸 알림 수신 주소
  password_hash VARCHAR(100) NOT NULL,            -- {bcrypt}$2a$10$... (평문 저장 금지)
  birth_date    DATE         NULL,
  gender        TINYINT(1)   NULL,
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,  -- 지원 이력 보존을 위해 하드 삭제 대신 비활성화
  PRIMARY KEY (applicant_id),
  -- 이메일 하나 = 구직자 한 명. 로그인 요청이 {email, password} 만으로 끝난다.
  UNIQUE KEY uq_applicant_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE education (
  education_id    BIGINT       NOT NULL AUTO_INCREMENT,
  applicant_id    BIGINT       NOT NULL,
  degree_level_id SMALLINT     NOT NULL,
  major_id        INT          NOT NULL,
  name            VARCHAR(100) NOT NULL,            -- 학교명
  PRIMARY KEY (education_id),
  UNIQUE KEY uq_education (applicant_id, degree_level_id),  -- 학위당 1행 + 학사 전공 조회 인덱스
  KEY idx_education_major (major_id),
  CONSTRAINT fk_edu_applicant FOREIGN KEY (applicant_id)    REFERENCES applicant(applicant_id),
  CONSTRAINT fk_edu_degree    FOREIGN KEY (degree_level_id) REFERENCES degree_level(degree_level_id),
  CONSTRAINT fk_edu_major     FOREIGN KEY (major_id)        REFERENCES major(major_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE career (
  career_id        BIGINT       NOT NULL AUTO_INCREMENT,
  applicant_id     BIGINT       NOT NULL,
  position_type_id SMALLINT     NOT NULL,           -- 해당 경력의 직무(BE/FE)
  name             VARCHAR(100) NOT NULL,           -- 직장명
  start_dt         DATETIME     NOT NULL,
  end_dt           DATETIME     NULL,               -- NULL=재직중
  PRIMARY KEY (career_id),
  KEY idx_career_applicant_pos (applicant_id, position_type_id),  -- [3-4] 직무별 경력연수 집계
  CONSTRAINT fk_career_applicant FOREIGN KEY (applicant_id)     REFERENCES applicant(applicant_id),
  CONSTRAINT fk_career_position  FOREIGN KEY (position_type_id) REFERENCES position_type(position_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE career_skill (
  career_skill_id BIGINT NOT NULL AUTO_INCREMENT,
  career_id       BIGINT NOT NULL,
  skill_id        INT    NOT NULL,
  PRIMARY KEY (career_skill_id),
  -- [3-3] 동일 경력 내 동일 스킬 중복 등록 금지
  UNIQUE KEY uq_career_skill (career_id, skill_id),
  KEY idx_career_skill_skill (skill_id),
  CONSTRAINT fk_cs_career FOREIGN KEY (career_id) REFERENCES career(career_id),
  CONSTRAINT fk_cs_skill  FOREIGN KEY (skill_id)  REFERENCES skill(skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 채용공고 + 공고 요구조건(스킬/경력/학력)을 데이터로 보유
-- ============================================================
CREATE TABLE job_posting (
  job_posting_id   BIGINT       NOT NULL AUTO_INCREMENT,
  company_id       BIGINT       NOT NULL,
  position_type_id SMALLINT     NOT NULL,
  title            VARCHAR(150) NOT NULL,
  content          TEXT         NULL,
  open_dt          DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
  close_dt         DATETIME     NULL,
  is_open          TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (job_posting_id),
  KEY idx_jp_company (company_id, is_open),         -- 멀티테넌시: 회사별 공고 (company_id 선두)
  CONSTRAINT fk_jp_company  FOREIGN KEY (company_id)       REFERENCES company(company_id),
  CONSTRAINT fk_jp_position FOREIGN KEY (position_type_id) REFERENCES position_type(position_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE job_posting_skill (                     -- 공고 필수스킬
  job_posting_skill_id BIGINT NOT NULL AUTO_INCREMENT,
  job_posting_id       BIGINT NOT NULL,
  skill_id             INT    NOT NULL,
  PRIMARY KEY (job_posting_skill_id),
  UNIQUE KEY uq_jp_skill (job_posting_id, skill_id),
  CONSTRAINT fk_jps_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting(job_posting_id),
  CONSTRAINT fk_jps_skill   FOREIGN KEY (skill_id)       REFERENCES skill(skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE job_posting_career (                    -- 공고 요구 직무/경력연수
  job_posting_career_id BIGINT   NOT NULL AUTO_INCREMENT,
  job_posting_id        BIGINT   NOT NULL,
  position_type_id      SMALLINT NOT NULL,
  career_years          SMALLINT NOT NULL,           -- 요구 최소 연차 (하드코딩 대체)
  PRIMARY KEY (job_posting_career_id),
  UNIQUE KEY uq_jp_career (job_posting_id, position_type_id),
  CONSTRAINT fk_jpc_posting  FOREIGN KEY (job_posting_id)   REFERENCES job_posting(job_posting_id),
  CONSTRAINT fk_jpc_position FOREIGN KEY (position_type_id) REFERENCES position_type(position_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE job_posting_education (                 -- 공고 요구 학력/전공
  job_posting_education_id BIGINT   NOT NULL AUTO_INCREMENT,
  job_posting_id           BIGINT   NOT NULL,
  degree_level_id          SMALLINT NOT NULL,
  major_id                 INT      NOT NULL,
  PRIMARY KEY (job_posting_education_id),
  UNIQUE KEY uq_jp_education (job_posting_id, degree_level_id, major_id),
  CONSTRAINT fk_jpe_posting FOREIGN KEY (job_posting_id)  REFERENCES job_posting(job_posting_id),
  CONSTRAINT fk_jpe_degree  FOREIGN KEY (degree_level_id) REFERENCES degree_level(degree_level_id),
  CONSTRAINT fk_jpe_major   FOREIGN KEY (major_id)        REFERENCES major(major_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. ★ 핵심 테이블 (3-5): application / stage / career_skill
-- ============================================================

-- 4-1. application : 지원 연결 + company_id 격리 + 현재 단계(stage_type_id) 보유
CREATE TABLE application (
  application_id BIGINT   NOT NULL AUTO_INCREMENT,
  company_id     BIGINT   NOT NULL,                 -- 멀티테넌시 격리키
  job_posting_id BIGINT   NOT NULL,
  applicant_id   BIGINT   NOT NULL,
  stage_type_id  SMALLINT NOT NULL,                 -- 현재 단계(스냅샷). 이력은 stage 테이블
  created_dt     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (application_id),
  -- [3-3] 동일 회사 내 동일 지원자가 동일 공고에 중복 지원 금지
  --       선두 company_id → 멀티테넌시 격리 + 합격자 쿼리 (company_id,…) 선두 인덱스 겸용
  UNIQUE KEY uq_application_tenant (company_id, job_posting_id, applicant_id),
  KEY idx_application_applicant (applicant_id),     -- 글로벌 지원자 역조회
  CONSTRAINT fk_app_company   FOREIGN KEY (company_id)     REFERENCES company(company_id),
  CONSTRAINT fk_app_posting   FOREIGN KEY (job_posting_id) REFERENCES job_posting(job_posting_id),
  CONSTRAINT fk_app_applicant FOREIGN KEY (applicant_id)   REFERENCES applicant(applicant_id),
  CONSTRAINT fk_app_stage_type FOREIGN KEY (stage_type_id) REFERENCES stage_type(stage_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4-2. stage : ATS 상태전이 이력 로그 (누가/언제/사유)
CREATE TABLE stage (
  stage_id       BIGINT      NOT NULL AUTO_INCREMENT,
  application_id BIGINT      NOT NULL,
  stage_type_id  SMALLINT    NOT NULL,              -- 문자열 상태값 대신 lookup FK
  content        TEXT        NULL,                  -- 전이 사유/메모
  -- 처리자. 문자열이 아니라 FK 로 묶어 "누가 했는지"를 DB가 보장한다.
  -- 담당자가 퇴사해도 이력이 남아야 하므로 company_user 는 하드 삭제하지 않고 is_active=0 으로 둔다.
  --
  -- NULL 을 허용하는 이유: 첫 단계(서류접수)는 구직자 본인이 지원하면서 만든다. 그 행에는
  -- 채울 company_user 가 없다. 즉 NULL = '구직자 본인 행위', NOT NULL = '기업 담당자 행위'다.
  -- 누가 지원했는지는 application.applicant_id 로 확정되므로 행위자가 유실되지는 않는다.
  created_by     BIGINT      NULL,
  created_dt     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (stage_id),
  KEY idx_stage_app (application_id, stage_id),     -- [3-4] 지원별 전이이력 조회
  KEY idx_stage_created_by (created_by),
  CONSTRAINT fk_stage_app  FOREIGN KEY (application_id) REFERENCES application(application_id),
  CONSTRAINT fk_stage_type FOREIGN KEY (stage_type_id) REFERENCES stage_type(stage_type_id),
  CONSTRAINT fk_stage_user FOREIGN KEY (created_by)     REFERENCES company_user(company_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4-3. (career_skill 은 위 2장 참조 — 3-3 UNIQUE 포함)

-- ============================================================
-- 5. 매칭(AI 추천)
-- ============================================================
CREATE TABLE matching (
  matching_id    BIGINT   NOT NULL AUTO_INCREMENT,
  application_id BIGINT   NOT NULL,
  score          SMALLINT NOT NULL,
  matched_dt     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (matching_id),
  UNIQUE KEY uq_matching_application (application_id),
  CONSTRAINT fk_matching_app FOREIGN KEY (application_id) REFERENCES application(application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
