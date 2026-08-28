# Ainjob ATS API 개발 과제

1차 과제 시 설계한 결과물을 **Java / Spring Boot** 로 구현한 결과물입니다.

| 요구사항 | 구현 | 확인 방법 |
|---|---|---|
| 1. ATS 합격자 필터 API<br> - 과제 SQL -> SpringBoot API<br> - DB 연동 (SQL query 연결)<br> - company_id 필터 | `GET /api/v1/companies/job-postings/{jobPostingId}/passed-applicants`<br> - 구현 2종 (JPA / 네이티브 SQL)<br> - 과제의 요구사항이, 1차 과제로 제출한 SQL을 그대로 네이티브 SQL로 연결하는 것을 요구하는 것인지, 이 쿼리를 JPA 등의 다른 방식으로 표현해도 되는 것인지 애매하여, 통상적인 방법(JPA)와 과제 전용(네이티브 SQL)을 둘 다 구현함. | `AtsApiIntegrationTest` — 기대 결과 3케이스 재현 + 두 구현 결과 동등성 |
| 2. `company_id` 없이 요청하면? | 시큐리티 필터 체인에서 **401 `TOKEN_REQUIRED`** 로 차단, 조회 계층 미도달 | [`PassedApplicantControllerTest`](src/test/java/com/ainjob/ats/applicant/PassedApplicantControllerTest.java) |
| 3. (가점) 지원자 상태 변경 시 이메일 알림 | `PATCH /api/v1/companies/applications/{id}/stage` → `AFTER_COMMIT` 비동기 SMTP 발송 | [`StageChangedEmailListenerTest`](src/test/java/com/ainjob/ats/notification/StageChangedEmailListenerTest.java) |

- 언어/프레임워크: **Java 21 / Spring Boot 3.5.16** (Gradle Wrapper 포함)
- 인증: **Spring Security + JWT 로그인, 주체 2종** — 구직자(`applicant`) / 기업 담당자(`company_user`). 어느 쪽인지가 `member_type` 클레임으로 실린다
- 인가: **회원 구분 + 역할 2단** — 구직자 전용 경로와 기업 전용(`/companies/**`) 경로가 갈리고, 기업 쪽 쓰기(공고 등록 / 공고 마감 / 상태 전이)는 OWNER / RECRUITER 만
- 경로 규칙: **`/companies` 가 붙으면 기업용, 없으면 공개·구직자용.** 접두어에 `company_id` 는 담기지 않는다 — 테넌트는 토큰에서만 나온다
- DB 접근: **Spring Data JPA (Hibernate)** — 합격자 필터만 JPA(기본) / 과제 원본 SQL 두 구현을 설정으로 전환 (아래 3-1)
- 스키마: `03_AINJOB_schema.sql` 이 정본. `ddl-auto: validate` 로 **엔티티가 그 스키마와 맞는지 기동 시 검증만** 한다
- 설정: **자격증명은 저장소에 없습니다.** DB 접속 정보와 SMTP 계정은 커밋하지 않는 `config/application-local.yml` 한 곳에만 있습니다 (아래 1-3)
- 테스트: 단위/슬라이스 **87건** + 실 DB 통합 **63건** = 총 **150건 통과**. 통합이 함께 돌므로 `./gradlew test` 는 DB 를 전제합니다 (아래 1-6)

---

## 1. 실행 방법

**실행 단위는 실행 가능 jar 하나입니다.** Gradle·소스 없이 `java -jar` 만으로 뜹니다
(Tomcat·JDBC 드라이버·설정이 모두 들어 있는 fat jar, 약 60MB).

### 1-1. 빌드

```bash
./gradlew bootJar
# → build/libs/ats-api-0.0.1-SNAPSHOT.jar
```

다른 PC로 옮길 때 챙길 것은 jar 하나와 DB 적재용 SQL 두 개뿐입니다.

```
옮길 폴더/
├── ats-api.jar              ← 위에서 만든 jar (이름은 바꿔도 됨)
├── config/
│   └── application-local.yml  ← DB 접속 정보 + SMTP 계정 (아래 1-3). 저장소에 없습니다
└── sql/
    ├── 03_AINJOB_schema.sql
    └── 03_AINJOB_dummy.sql
```

> **`config/application-local.yml` 은 저장소에 포함되어 있지 않습니다.** 자격증명이라 커밋하지
> 않았고, **메일로 따로 보내 드립니다.** 받은 파일을 위 위치에 두면 됩니다. 직접 작성해도 되며
> 형식은 1-3 에 있습니다. **이 파일 없이는 앱이 뜨지 않습니다** — DB 접속 정보가 없기 때문이며,
> 자격증명이 저장소에 남는 것보다 "없으면 즉시 실패"가 낫다고 보고 자리표시자를 두지 않았습니다.

### 1-2. 실행할 PC 준비 — DB 적재

MariaDB(또는 MySQL)가 떠 있어야 하고, 스키마와 더미 데이터가 들어 있어야 합니다.

```bash
mariadb -u root -p -e "CREATE DATABASE IF NOT EXISTS ainjob DEFAULT CHARSET utf8mb4"
mariadb -u root -p ainjob < db/03_AINJOB_schema.sql
mariadb -u root -p ainjob < db/03_AINJOB_dummy.sql
```

`03_AINJOB_schema.sql` 은 맨 앞에서 테이블을 전부 `DROP` 하므로 위 두 줄이면 언제든 초기 상태로
돌아갑니다. **이미 적재해 둔 DB 가 있다면 다시 적재해야 합니다** — 구직자 로그인을 위해
`applicant` 에 `password_hash` / `is_active` 가 추가되고 `stage.created_by` 가 NULL 허용으로
바뀌어서, 예전 스키마 그대로면 `ddl-auto: validate` 가 불일치를 잡아 기동이 실패합니다.

더미 데이터도 다시 넣어야 합니다. 공고 4건의 `close_dt` 가 `2025-12-31` → `2027-12-31` 로 바뀌었고,
**모집 기간이 지난 공고는 공개 목록·상세에서 사라지고 지원도 받지 않기 때문에**(아래 6-1) 예전
더미 그대로면 시연 공고가 하나도 보이지 않습니다.

스키마 마이그레이션 도구(Flyway·Liquibase)는 쓰지 않습니다. **이 과제에서는 스키마 자체가 제출물**
이므로 손으로 쓴 `03_AINJOB_schema.sql` 이 정본이고, 애플리케이션은 그것을 고치지 않고 검증만 합니다
(`ddl-auto: validate`).

### 1-3. 설정 — **`config/application-local.yml` 을 둔다**

**저장소에는 자격증명이 없습니다.** `src/main/resources/application.yml` 에 DB 접속 정보와 SMTP
계정 키가 **아예 존재하지 않습니다** — 빈 자리표시자도 두지 않았습니다. 실제 값은 커밋하지 않는
파일 하나에만 있습니다.

```
ats-api/config/application-local.yml     ← .gitignore 로 제외. 메일로 따로 전달
```

클래스패스(`src/main/resources`) 가 아니라 `config/` 에 둔 이유는 `bootJar` 산출물 안에 포함되지
않게 하기 위해서입니다 — **jar 를 그대로 건네도 자격증명이 따라가지 않습니다.**

이 파일은 `application.yml` 의 `spring.config.import` 가 읽어 들이며, 경로는 **실행한 위치(현재 작업
디렉터리) 기준**입니다.

- `./gradlew bootRun` / `./gradlew test` → 작업 디렉터리가 `ats-api/` 라 그대로 잡힙니다
- `java -jar ats-api.jar` → **jar 옆에 `config/` 폴더**를 두면 됩니다

#### 파일 형식

받은 파일을 그대로 쓰면 되고, 직접 작성한다면 이 다섯 개가 전부입니다.

```yaml
# config/application-local.yml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/ainjob?characterEncoding=UTF-8
    username: root
    password: 실행할-PC의-DB-비밀번호
  mail:
    username: {SMTP 계정}          # 이 주소가 그대로 발신자(From)가 됩니다
    password: {앱 비밀번호}
```

SMTP 계정을 준비하기 어렵다면 `mail` 절을 통째로 빼고, 대신 알림을 끄십시오. 알림 흐름은 콘솔
로그(DRY-RUN)로 그대로 확인할 수 있습니다.

```yaml
# jar 옆 application.yml 에 두거나 커맨드라인 인자로 넘겨도 됩니다
ainjob:
  mail:
    enabled: false
```

> **이 파일이 없으면 기동에 실패합니다.** `import` 는 `optional:` 이라 파일 자체는 없어도 넘어가지만,
> `spring.datasource.url` 이 비어 있어 DataSource 구성 단계에서 멈춥니다. 의도한 동작입니다.

#### 나머지 설정을 바꾸려면 — jar 옆에 `application.yml`

jar 안에도 `application.yml` 이 들어 있지만, **jar 를 실행하는 디렉터리에 같은 이름의 파일을 두면
그쪽이 이깁니다.** jar 를 다시 빌드할 필요가 없습니다. 바꾸는 값만 적으면 되고, 적지 않은 항목은
jar 안의 값이 그대로 쓰입니다.

```bash
java -jar ats-api.jar
```

> 설정 파일을 다른 위치에 두려면 경로를 명시하십시오.
>
> ```bash
> java -jar /path/to/ats-api.jar --spring.config.additional-location=file:/path/to/설정폴더/
> ```

Windows 에서 한글이 깨지면 `java -Dfile.encoding=UTF-8 -jar ats-api.jar` 로 실행합니다.

### 1-4. 값 몇 개만 바꾸는 경우 — 커맨드라인 인자

파일을 만들기 번거로우면 실행할 때 직접 넘겨도 됩니다(파일보다 우선합니다).
`config/application-local.yml` 없이 이 방법만으로도 뜹니다.

```bash
java -jar ats-api.jar \
  --spring.datasource.url="jdbc:mariadb://localhost:3306/ainjob?characterEncoding=UTF-8" \
  --spring.datasource.username=root \
  --spring.datasource.password=**** \
  --ainjob.auth.jwt-secret=최소-32바이트-이상의-서명키-문자열 \
  --ainjob.mail.enabled=false \
  --server.port=8080
```

Windows 에서 한글이 깨지면 `java -Dfile.encoding=UTF-8 -jar ats-api.jar` 로 실행합니다.

### 1-5. 호출

```bash
# ── 구직자: 토큰 없이 공고를 본다 ──────────────────────────────
curl -s http://localhost:8080/api/v1/job-postings
curl -s http://localhost:8080/api/v1/job-postings/1

# ① 구직자 로그인 (접두어 없는 경로가 구직자 몫이다)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"h.json248@gmail.com","password":"applicant1234"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600,
#    "applicantId":7,"memberType":"APPLICANT","name":"문지후"}

# ② 지원 — 본문에 지원자 식별자가 없다. 토큰이 곧 지원자다
curl -s -X POST http://localhost:8080/api/v1/job-postings/1/applications \
  -H "Authorization: Bearer eyJ..." -H "Content-Type: application/json" \
  -d '{"reason":"백엔드 6년차입니다."}'

# ── 기업 담당자 ───────────────────────────────────────────────
# ③ 기업 로그인 — 요청에 companyId 가 없다는 점이 핵심이다
curl -s -X POST http://localhost:8080/api/v1/auth/companies/login \
  -H "Content-Type: application/json" \
  -d '{"email":"recruiter@company1.com","password":"ainjob1234!"}'
# → {"accessToken":"eyJ...","tokenType":"Bearer","expiresIn":3600,
#    "companyUserId":2,"companyId":1,"memberType":"COMPANY_USER",
#    "role":"RECRUITER","name":"기업1 채용담당"}

# ④ 발급받은 토큰으로 합격자 조회 — 경로에도 companyId 는 없다
curl -s http://localhost:8080/api/v1/companies/job-postings/1/passed-applicants \
  -H "Authorization: Bearer eyJ..."
```

더미 구직자 14명 (비밀번호는 모두 `applicant1234`) — 예: `h.json248@gmail.com`(문지후),
`2win@naver.com`(이서윤).

> **`h.json248@gmail.com`(applicant_id=7, 문지후) 은 요구사항 3 이메일 발송을 실제로 확인하기 위한
> 데이터입니다.** 나머지 13명의 주소는 수신함이 없는 가짜 주소라 발송 결과를 눈으로 볼 수 없어,
> 한 명만 실제 수신 가능한 주소로 두었습니다. 이 지원자의 `application_id=11` 은 더미 15건 중
> 유일하게 종결이 아닌(면접) 지원 건이라 상태 전이를 그대로 실행할 수 있습니다 — 나머지 14건은
> 모두 최종합격이라 FORWARD 가 막힙니다. 자세한 확인 절차는 [5-4](#5-4-실제-발송-확인-더미-데이터-기준) 를 보십시오. 기업 담당자 계정 5개 (비밀번호는 모두 `ainjob1234!`, DB에는 bcrypt 해시로 저장):

| 이메일 | 소속 | 역할 | 할 수 있는 일 |
|---|---|---|---|
| `owner@company1.com` | 기업1 | OWNER | 조회 + 쓰기 |
| `recruiter@company1.com` | 기업1 | RECRUITER | 조회 + 쓰기 |
| `viewer@company1.com` | 기업1 | VIEWER | 조회만 |
| `recruiter@company2.com` | 기업2 | RECRUITER | 조회 + 쓰기 |
| `viewer@company2.com` | 기업2 | VIEWER | 조회만 |

### 1-6. 테스트

```bash
./gradlew test --no-daemon               # 단위/슬라이스 87건 + 실 DB 통합 63건 = 총 150건
```

통합 테스트는 별도 스위치 없이 항상 실행됩니다. 그래서 **`./gradlew test` 는 스키마·더미가 적재된
DB 와 접속 정보를 담은 `config/application-local.yml` 을 전제합니다** — 둘 중 하나라도 없으면 통합
63건이 컨텍스트 로딩에서 실패합니다. 통합 테스트는 각각 `@Transactional` 롤백이라 더미 데이터를
오염시키지 않습니다.

테스트는 **`src/test/resources/application.yml`** 을 씁니다. 클래스패스에서 `application.yml` 은 한
번만 읽히고 Gradle 이 테스트 리소스를 앞에 두므로, 이 파일이 메인 설정을 **일부 덮어쓰는 게 아니라
통째로 대신합니다.** 그래서 바꿀 필요가 없는 항목까지 그대로 옮겨 적혀 있고, **값은 메인 설정과
전부 동일합니다.**

설정을 환경변수로 갈아 끼우지 않는 것이 이 프로젝트의 방침이라, 테스트만 다른 값을 쓰면 "테스트는
통과하는데 실행하면 다르게 도는" 경로가 생깁니다. 서명 키(`ainjob.auth.jwt-secret`)도 알림
스위치(`ainjob.mail.enabled`)도 메인과 같은 값입니다.

**자격증명은 이 파일에도 없습니다.** 테스트 설정의 `spring.config.import` 가 메인과 **같은**
`config/application-local.yml` 을 가리키므로, DB 접속 정보와 SMTP 계정을 두 군데 적을 필요가
없습니다. 그래서 통합 테스트도 그 파일이 있어야 돕니다 — 없으면 컨텍스트가 뜨지 못해
`./gradlew test` 가 실패합니다.

> 알림이 켜져 있어도 테스트가 실제로 메일을 보내지는 않습니다. 전이를 수행하는 테스트는 모두
> `@Transactional` 롤백이라 `AFTER_COMMIT` 리스너가 발동하지 않기 때문입니다. **커밋되는 전이
> 테스트를 새로 추가한다면 이 전제가 깨지므로 그때 다시 검토해야 합니다.**

### 1-7. 자주 걸리는 것 세 가지

| 증상 | 원인 / 해결 |
|---|---|
| `HHH000339 Unknown column 'RESERVED'` 후 기동 실패 | **MariaDB 서버에 MySQL 드라이버**를 쓴 경우. Hibernate 가 메타데이터를 조회할 때 Connector/J 9.x 가 MySQL 8 전용 컬럼을 읽으려 하는데 MariaDB 에는 없습니다. `url` 을 `jdbc:mariadb://...` 로 (MySQL 서버면 반대로 `jdbc:mysql://...`) |
| `Failed to configure a DataSource: 'url' attribute is not specified` | `config/application-local.yml` 이 없거나 실행 위치가 달라 못 찾은 경우. 이 저장소에는 DB 접속 정보가 없습니다(1-3). 파일을 jar 옆 `config/` 에 두거나 `--spring.datasource.url=...` 로 넘기십시오 |
| `ainjob.auth.jwt-secret 은 최소 32바이트여야 합니다` | HS256 요건. 서명 키를 32바이트 이상으로. 값을 아예 비우면 임의 키를 만들고 WARN 을 남깁니다(재기동 시 기존 토큰 무효) |
| `spring.mail.username 이 필요합니다` | `ainjob.mail.enabled=true` 인데 SMTP 계정이 없음. 시연만 할 거면 `enabled: false` (알림이 콘솔 로그로 출력됩니다) |

### 1-8. 개발 중 실행

소스가 있는 상태에서는 jar 를 만들지 않고 바로 띄울 수 있습니다.
이때는 `src/main/resources/application.yml` 이 그대로 쓰입니다.

```bash
./gradlew bootRun
```

---

## 2. 인증 · 인가

### 2-1. 왜 회원 테이블을 만들었나

설계 단계의 도메인 표는 Tenant 도메인의 책임을 **"기업 가입·계정·인증 주체"** 로 이미 선언하고 있는데,
ERD에는 그 계정 테이블이 없었습니다. 그 결과 초기 구현에서는 호출자가 `company_id` 를 **자기 신고**하는
구조가 되어, 기업1 담당자가 기업2 데이터를 조회할 수 있었습니다.

`company_user` / `company_role` 두 테이블을 추가해 이 구멍을 닫았습니다. 도메인 표가 이미 약속한 것을
ERD에 반영한 것이므로, 설계를 바꾼 게 아니라 **설계와 스키마의 불일치를 해소한 것**입니다.

```
POST /api/v1/auth/companies/login
{ "email": "recruiter@company2.com", "password": "..." }
  → { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
      "companyUserId": 4, "companyId": 2, "memberType": "COMPANY_USER",
      "role": "RECRUITER", "name": "기업2 채용담당" }
```

**요청에 `companyId` 가 없다는 점이 핵심입니다.** 소속 기업은 인증에 성공한 계정 행에서 읽으므로,
기업1 계정으로 로그인해서 기업2 토큰을 받을 경로가 존재하지 않습니다.

#### 구직자도 로그인 주체입니다

이 서비스는 ATS(기업 내부 채용관리 툴)가 아니라 **잡포털**입니다. 지원자가 곧 사용자이므로,
`applicant` 에 `password_hash` / `is_active` 를 더해 두 번째 계정 테이블로 만들었습니다.

```
POST /api/v1/auth/login          ← 잡포털의 기본 사용자는 구직자다
{ "email": "h.json248@gmail.com", "password": "..." }
  → { "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600,
      "applicantId": 7, "memberType": "APPLICANT", "name": "문지후" }
```

구직자 토큰에는 **`companyId` 도 `role` 도 없습니다.** 어느 기업에도 속하지 않고 역할 구분도
없으므로, 없는 값을 만들어 싣지 않습니다. 그 결과 이 토큰으로는 `TenantContext` 를 통과할 수
없고 `/api/v1/companies/**` 어느 경로에도 도달하지 못합니다.

**회원 구분은 클라이언트가 고를 수 없습니다.** 요청 본문은 두 경로가 같지만 조회하는 계정
테이블이 다르므로, 구직자 계정으로 기업 로그인을 시도하면 계정을 찾지 못해 401 입니다.

### 2-2. 테이블

```sql
CREATE TABLE company_role (              -- 문자열 상태값 금지 원칙 → lookup FK
  role_id SMALLINT NOT NULL AUTO_INCREMENT,
  code    VARCHAR(20) NOT NULL,          -- OWNER / RECRUITER / VIEWER
  ...
);

CREATE TABLE company_user (
  company_user_id BIGINT       NOT NULL AUTO_INCREMENT,
  company_id      BIGINT       NOT NULL,   -- 멀티테넌시 격리키
  role_id         SMALLINT     NOT NULL,
  email           VARCHAR(120) NOT NULL,
  password_hash   VARCHAR(100) NOT NULL,   -- {bcrypt}$2a$10$... (평문 저장 금지)
  is_active       TINYINT(1)   NOT NULL DEFAULT 1,
  UNIQUE KEY uq_company_user_email (email),
  KEY idx_company_user_company (company_id, is_active),
  ...
);
```

- **이메일은 전역 UNIQUE** — 한 이메일 = 한 기업 소속. 로그인이 `{email, password}` 만으로 끝나고,
  `company_id` 를 클라이언트가 지정할 여지가 사라집니다.
- **하드 삭제하지 않습니다** — `stage.created_by` 가 FK로 묶여 있어 담당자가 퇴사해도 이력이 남아야 합니다.
  탈퇴는 `is_active = 0` 으로 처리합니다.
- 계정 열거 방지: "없는 계정 / 비활성 / 비밀번호 불일치"를 모두 같은 401 `INVALID_CREDENTIALS` 로 응답하고,
  계정이 없을 때도 더미 해시로 한 번 비교해 응답 시간을 맞춥니다.

구직자 쪽은 기존 `applicant` 테이블에 계정 컬럼을 더했습니다. 테이블을 새로 파지 않은 이유는
**이미 이메일이 전역 UNIQUE 라 식별자 역할을 그대로 하기 때문**입니다.

```sql
CREATE TABLE applicant (
  applicant_id  BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(50)  NOT NULL,
  email         VARCHAR(120) NOT NULL,            -- 로그인 식별자 겸 알림 수신 주소
  password_hash VARCHAR(100) NOT NULL,            -- 추가
  birth_date    DATE         NULL,
  gender        TINYINT(1)   NULL,
  is_active     TINYINT(1)   NOT NULL DEFAULT 1,  -- 추가
  UNIQUE KEY uq_applicant_email (email)
  -- company_id 는 여전히 없다. 구직자는 글로벌 풀이다
);
```

**계정 테이블이 둘로 갈려 있는 것 자체가 격리입니다.** 한쪽 계정이 다른 쪽 권한을 얻을 경로가
스키마 수준에서 존재하지 않고, 로그인 시 어느 테이블에서 찾았는지가 곧 `member_type` 이 됩니다.

### 2-3. 스키마 변경 이력

API 구현을 진행하면서 `03_AINJOB_schema.sql` 에 추가·변경한 부분입니다.
**이 저장소의 `03_AINJOB_schema.sql` 이 스키마의 유일한 정본이며**, 아래가 원래 설계 대비 델타입니다.

| 구분 | 대상 | 내용 | 이유 |
|---|---|---|---|
| 추가 | `company_role` | OWNER / RECRUITER / VIEWER lookup 테이블 | 역할을 문자열이 아닌 FK로 |
| 추가 | `company_user` | 기업 소속 담당자 = 인증 주체 | 원래 ERD에 계정 테이블이 없어 `company_id` 사칭이 가능했음 |
| 변경 | `stage.created_by` | `VARCHAR(50)` → `BIGINT` FK → `company_user` | "누가 했는지"를 로직이 아닌 DB 제약으로 보장 |
| 추가 | `idx_stage_created_by` | `stage(created_by)` 인덱스 | FK 조인 대비 |
| 추가 | `applicant.password_hash` · `is_active` | 구직자를 두 번째 로그인 주체로 | 잡포털은 구직자가 직접 지원한다. 담당자 대행 접수만 있으면 지원자 계정이 성립하지 않음 |
| 변경 | `stage.created_by` **NULL 허용** | `NOT NULL` → `NULL` | 첫 단계(서류접수)는 구직자 본인이 만든다. 그 행에는 채울 `company_user` 가 없다 — NULL = 본인 행위, NOT NULL = 담당자 행위 |

`application` 과 `career_skill` 은 **변경 없습니다** — 멀티테넌시 핵심 3개 테이블 중 둘은 원래 설계 그대로입니다.
JPA 로 전환하면서도 스키마는 건드리지 않았습니다(아래 9장 `@Version` 항목 참고).

### 2-4. 인가 — 회원 구분 + 역할 2단

권한 규칙은 `SecurityConfig` 한 곳에 모여 있습니다. **역할을 보기 전에 회원 구분부터 봅니다.**

| 엔드포인트 | 비회원 | 구직자 | VIEWER | RECRUITER | OWNER |
|---|:---:|:---:|:---:|:---:|:---:|
| `POST /auth/login` · `/auth/companies/login` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST /applicants` (회원가입) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /job-postings` · `/{id}` (공개 공고) | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET /applicants/{id}` (본인 프로필) | 401 | **본인만** | 403 | 403 | 403 |
| `POST /job-postings/{id}/applications` (지원) | 401 | ✅ | 403 | 403 | 403 |
| `GET /companies/job-postings` · `/{id}` | 401 | 403 | ✅ | ✅ | ✅ |
| `GET /companies/job-postings/{id}/passed-applicants` | 401 | 403 | ✅ | ✅ | ✅ |
| `GET /companies/job-postings/{id}/applicants/{id}` | 401 | 403 | ✅ | ✅ | ✅ |
| `POST /companies/job-postings` (공고 등록) | 401 | 403 | **403** | ✅ | ✅ |
| `PATCH /companies/job-postings/{id}/close` (마감) | 401 | 403 | **403** | ✅ | ✅ |
| `PATCH /companies/applications/{id}/stage` (상태 전이) | 401 | 403 | **403** | ✅ | ✅ |

`JwtAuthenticationConverter` 가 토큰 하나에서 권한을 최대 두 개 만듭니다.

```
기업 담당자 : member_type=COMPANY_USER, role=RECRUITER → [ROLE_COMPANY_USER, ROLE_RECRUITER]
구직자      : member_type=APPLICANT    (role 클레임 없음) → [ROLE_APPLICANT]
```

회원 구분을 역할과 같은 형식으로 실어 두면 인가 규칙에서 `hasRole` 하나로 둘 다 표현할 수
있습니다. **구직자에게 역할 클레임을 주지 않는 것이 핵심**입니다 — 없는 권한은 흉내 낼 수 없으므로,
구직자 토큰은 역할 규칙(`hasAnyRole(WRITE_ROLES)`)도 회원 구분 게이트(`hasRole(COMPANY_USER)`)도
통과하지 못합니다. 반대로 기업 토큰에는 `ROLE_APPLICANT` 가 없어 구직자 전용 경로를 통과하지
못합니다. 어느 쪽이든 컨트롤러에 도달조차 하지 않습니다.

#### 인가만으로 부족한 두 곳

- **본인 프로필 조회** — 인가는 "구직자인가"까지만 판정합니다. "그 구직자인가"는 경로의 식별자를
  토큰 subject 와 대조해 정하며, 다르면 403 `NOT_OWN_PROFILE` 입니다. **존재 확인보다 본인 확인이
  먼저**입니다 — 순서를 뒤집으면 404/403 차이로 가입자 수를 셀 수 있습니다.
- **기업의 지원자 열람** — 지원자에게는 `company_id` 가 없으므로 지원자만으로는 격리 조건을 만들 수
  없습니다. 그래서 경로를 공고 하위(`/companies/job-postings/{id}/applicants/{id}`)에 두고 **공고를
  테넌트 앵커로** 씁니다. 남의 회사 공고는 테넌트 필터가 아예 보이지 않게 하므로, 공고 없음 ·
  공고가 남의 것 · 지원 사실 없음이 **모두 404 로 동일합니다**(아래 7장).

> **구현하지 않은 것**(리프레시 토큰 · 비밀번호 재설정 · 로그인 실패 잠금 · 대칭키 선택의 한계 등)은
> [11장](#11-구현하지-않은-것-과제-범위-밖)에 인증 밖의 항목들과 함께 모아 두었습니다.

---

## 3. 요구사항 1 — ATS 합격자 필터 API

```
GET /api/v1/companies/job-postings/{jobPostingId}/passed-applicants
Authorization: Bearer {accessToken}
```

### 3-1. 구현이 두 개다 — 설정으로 고른다

요구사항 문구 "과제 SQL에서 작성한 쿼리를 API 엔드포인트 구현"이 **SQL 텍스트 그대로를 실행하라**는
뜻인지 **그 조회 기능을 API로 만들라**는 뜻인지 단정하기 어려웠습니다. 한쪽만 만들면 다른 해석에서
지적을 받습니다. 그래서 **둘 다 두고 기동 설정으로 전환**합니다.

전환은 `application.yml` 의 값 하나를 바꾸고 앱을 다시 띄우면 됩니다.

```yaml
ainjob:
  passed-applicant:
    strategy: JPA        # 기본값. 과제 원본 SQL로 돌리려면 NATIVE_SQL
```

| 전략 | 구현 | 실행되는 것 |
|---|---|---|
| `JPA` (기본) | [`JpaPassedApplicantFinder`](src/main/java/com/ainjob/ats/applicant/JpaPassedApplicantFinder.java) + [`PassedApplicantQueryRepository`](src/main/java/com/ainjob/ats/applicant/PassedApplicantQueryRepository.java) | Spring Data `@Query` (HQL) |
| `NATIVE_SQL` | [`NativeSqlPassedApplicantFinder`](src/main/java/com/ainjob/ats/applicant/NativeSqlPassedApplicantFinder.java) | `sql/passed-applicants.sql` **파일 그대로** |

**응답 JSON은 두 전략이 완전히 동일합니다.** 컨트롤러·서비스·DTO는 전략을 모릅니다. 테넌트 검증
(404/403)과 응답 조립은 전략과 무관하게 서비스에서 한 번만 하고, 구현체는 필터 조건만 책임집니다.

기동 시 어느 쪽이 활성인지 로그로 남깁니다:

```
INFO  c.a.a.applicant.PassedApplicantService : 합격자 필터 실행 전략 = JPA (ainjob.passed-applicant.strategy)
```

#### 두 구현이 같은 답을 낸다는 것을 테스트로 고정했습니다

구현이 둘이면 **둘이 일치한다는 사실 자체가 검증 대상**입니다.
`AtsApiIntegrationTest.bothFinderStrategiesAgree` 가 두 Finder를 나란히 주입해 기업1 BE / 기업2 BE /
기업2 FE 세 케이스에서 결과 리스트가 완전히 일치하는지 대조합니다(결과가 비는 케이스도 함께).
설정으로 빈을 하나만 만들지 않고 **두 구현을 항상 등록**해 둔 이유가 이 테스트입니다.

#### 왜 네이티브 쪽은 `@Query(nativeQuery = true)` 가 아닌가

어노테이션 속성은 **컴파일 타임 상수만** 받습니다. SQL을 자바 문자열로 옮겨 적어야 하고, 그러면
제출 파일과 실행 쿼리가 갈라집니다. `EntityManager.createNativeQuery()` 에 파일 내용을 그대로 넘기면
**제출물과 실행 쿼리가 같은 하나의 파일**로 유지됩니다.

#### 왜 JPA 쪽은 "다 읽어서 자바로 거르기"가 아닌가

그러면 지원자가 늘수록 무너집니다. 세 판정 규칙(학력 OR / 경력 AND / 스킬 관계 나눗셈)은 전부 HQL
서브쿼리로 표현 가능하므로 **필터링은 네이티브 버전과 똑같이 DB에서** 끝냅니다. 자바로 계산하는 것은
응답의 경력연수 하나뿐인데, JPQL이 SELECT 절 상관 스칼라 서브쿼리를 허용하지 않기 때문입니다.
그 계산도 이미 도메인에 있는 `Applicant.careerYearsOf()` 를 쓰며, 원본 SQL과 같은 방식(월 단위로
합산한 뒤 12로 나눔)입니다.

HQL 번역에서 원본과 다르게 쓴 곳은 두 군데이고 **의미는 동일**합니다.

| 원본 SQL | HQL | 이유 |
|---|---|---|
| `개월합 DIV 12 < 요구연차` | `개월합 < 요구연차 * 12` | HQL `/` 는 정수 나눗셈을 보장하지 않음. 정수에서 `floor(m/12) < y ⟺ m < 12y` 이고 경력 기간은 음수가 될 수 없음 |
| `NOW()` | `:now` 파라미터 | 요청 한 건 안에서 기준 시각이 고정되고 테스트가 결정적이 됨 |

> `timestampdiff` 는 표준 JPQL이 아니라 **Hibernate 6의 HQL 함수**입니다(방언별 번역). 순수 JPQL만으로는
> 두 시각의 개월 수 차이를 구할 방법이 없습니다 — JPA로 갈 때 유일하게 타협한 지점입니다.

### 3-2. 과제 SQL은 파일 그대로 둔다

`src/main/resources/sql/passed-applicants.sql` 이 앱이 실행하는 원본이고, [`db/03_AINJOB_query.sql`](db/03_AINJOB_query.sql) 이 1차 과제 제출 SQL입니다.
두 파일의 차이는 아래 두 가지뿐이며 **필터 조건(WHERE 절)은 1:1로 동일**합니다.

| 구분 | 제출 `03_AINJOB_query.sql` | 앱 `sql/passed-applicants.sql` |
|---|---|---|
| 파라미터 | `SET @company_id = 1;` 세션 변수 | `:companyId` / `:jobPostingId` 바인드 파라미터 |
| SELECT 절 | 화면 확인용 컬럼 | 응답 DTO용 식별자(`applicant_id`, `stage_type_id`, `code`) 추가 |

아래 음수 방지 `CASE` 는 **두 파일에 똑같이** 들어가 있습니다 — 제출 SQL 도 함께 고쳤습니다.
판정 로직이 갈리면 "제출물과 실행 쿼리가 같다"는 이 설계의 전제가 무너지기 때문입니다.

#### 왜 `CASE` 를 씌웠나 — 음수 개월수가 두 구현을 갈라놓는다

경력 한 건의 개월수가 음수가 될 수 있습니다. **시작일이 미래인 경력**(입사 예정으로 미리 등록)은
종료일이 없으면 "시작일 ~ 현재"가 되어 음수입니다. 그리고 음수 구간에서는 두 구현이 갈립니다 —
`DIV` 와 자바의 `/` 는 **0 방향 절단**인데, JPA 쪽 HQL 번역의 부등식(`개월합 < 요구연차 * 12`)은
**floor** 를 전제하기 때문입니다.

> 요구 연차 0, 개월합 −5 → 네이티브는 `-5 DIV 12 = 0`, `0 < 0` 이 거짓이라 **통과**시키고,
> HQL 은 `-5 < 0` 이 참이라 **탈락**시킵니다.

**입력을 막지 않고 계산으로 막기로 했습니다.** 입사 예정 경력이나 퇴사일이 정해진 경력을 미리
등록하는 것은 정상적인 입력이라 거부할 이유가 없습니다. 대신 세 계산 지점에 같은 규칙을 넣습니다.

| 지점 | 파일 |
|---|---|
| 네이티브 SQL | [`sql/passed-applicants.sql`](src/main/resources/sql/passed-applicants.sql) |
| JPA (HQL) | [`PassedApplicantQueryRepository`](src/main/java/com/ainjob/ats/applicant/PassedApplicantQueryRepository.java) |
| 응답의 경력연수 | [`Applicant.careerYearsOf`](src/main/java/com/ainjob/ats/domain/Applicant.java) |

```
아직 시작하지 않은 경력 (start_dt > now)          → 0개월
기간이 뒤집힌 경력      (end_dt < start_dt)       → 0개월   ← 직접 INSERT 방어
종료일이 미래인 경력    (퇴사 예정)                → 그대로 센다 (음수가 아니다)
```

**건별로 0으로 만드는 것**이 핵심입니다. 합계에 `GREATEST(0, ...)` 를 씌우면 미래 경력이 과거
경력을 깎아먹은 뒤에야 바닥이 걸립니다. 셋 중 하나라도 빠지면 두 구현이 갈리며,
`AtsApiIntegrationTest.bothFinderStrategiesAgreeWithFutureDatedCareer` 가 이를 회귀로 잡습니다
(네이티브 쪽 `CASE` 를 지우면 실제로 실패하는 것을 확인했습니다).
제출 SQL(`db/03_AINJOB_query.sql`)에도 같은 `CASE` 가 들어가 있어 두 파일의 판정이 갈리지 않습니다.
계산 규칙 자체는 `ApplicantCareerYearsTest` 9건이 DB 없이 고정합니다.

### 3-3. 응답 예시 (기업1 BE)

```json
{
  "companyId": 1,
  "companyName": "기업1",
  "jobPostingId": 1,
  "jobPostingTitle": "기업1 백엔드 개발자 채용",
  "totalCount": 3,
  "items": [
    { "applicationId": 1, "applicantId": 2, "applicantName": "이서윤",
      "email": "2win@naver.com", "positionCode": "BE", "careerYears": 10,
      "currentStageTypeId": 3, "currentStageCode": "HIRED", "currentStageName": "최종합격" },
    { "applicationId": 2, "applicantId": 3, "applicantName": "한예진", "...": "..." },
    { "applicationId": 3, "applicantId": 7, "applicantName": "문지후", "...": "..." }
  ]
}
```

### 3-4. 기대 결과 3케이스

| 케이스 | 토큰 | 공고 | 결과 |
|---|---|---|---|
| 기업1 BE 합격자 | `company_id=1` | `1` | 3명 — 이서윤 / 한예진 / **문지후** |
| 기업2 BE 합격자 | `company_id=2` | `3` | 2명 — 김철수 / 류태현 (**문지후 미포함**, 기업2에서는 면접 단계) |
| 기업2 FE 합격자 | `company_id=2` | `4` | 3명 — 안서연 / 황도윤 / 권유진 |

**두 전략 모두에서** 같은 결과가 나오는 것을 통합 테스트가 확인합니다.

---

## 4. 요구사항 2 — `company_id` 없이 요청하면?

### 결론: **401 Unauthorized (`TOKEN_REQUIRED`)로 차단되고, 조회 로직은 실행되지 않는다.**

```
$ curl -i http://localhost:8080/api/v1/companies/job-postings/1/passed-applicants

HTTP/1.1 401
{
  "code": "TOKEN_REQUIRED",
  "message": "액세스 토큰이 없습니다. Authorization: Bearer {token} 헤더가 필요합니다.",
  "path": "/api/v1/companies/job-postings/1/passed-applicants"
}
```

### 왜 400이 아니라 401인가

`company_id` 는 클라이언트가 보내는 **입력 값이 아니라 인증 결과**입니다.
검증된 JWT의 `company_id` 클레임에서만 읽으므로, 값이 없다는 것은 "파라미터 누락"이 아니라
**"인증 컨텍스트 없음"** 입니다.

### 설계상 더 중요한 점 — "필터를 깜빡할 수 있는 경로"를 없앤다

`company_id` 는 **경로/쿼리/헤더 어디에서도 받지 않습니다.** 요청 흐름은 다음과 같습니다.

```
로그인 → company_user 행에서 company_id·role 확정 → JWT 클레임에 봉인
요청  → SecurityFilterChain : 토큰 검증 실패 시 401
                             회원 구분(ROLE_COMPANY_USER) 부족 시 403
                             역할(OWNER/RECRUITER) 부족 시 403 으로 즉시 종료
                             └ DispatcherServlet 이 핸들러를 찾기도 전에 끝난다
      → Controller          : TenantContext.companyId() 로만 획득 (원시 타입 long)
      → Service             : companyId 를 필수 인자로 받음 / Aggregate 는 isOwnedBy() 로 자기 확인
      → DB                  : (company_id, ...) 선두 복합 인덱스 / UNIQUE 제약
```

경로에도 `company_id` 가 없습니다. 기업용 접두어를 `/companies/{companyId}` 가 아니라
`/companies` 로 둔 이유가 이것입니다 — **남의 회사를 지정할 자리가 URL 문법에 존재하지 않습니다.**

즉 **company_id 없이 실행 가능한 조회 코드 경로가 존재하지 않습니다.**
"필터가 빠져서 전체 테넌트 데이터가 조회되는" 사고는 실수로도 만들 수 없습니다.
`PassedApplicantControllerTest` 는 이 점을 `verify(service, never())` 로 검증합니다.

### 인접 케이스

| 요청 | 응답 | 이유 |
|---|---|---|
| 토큰 없음 | **401** `TOKEN_REQUIRED` | 인증 컨텍스트 없음 |
| 위조·만료 토큰 | **401** `INVALID_TOKEN` | 서명 검증 실패 — 클레임을 임의로 넣어도 통과 못 함 |
| 토큰은 유효하나 `company_id` 클레임 없음 | **401** `TENANT_REQUIRED` | 마지막 방어선 |
| 구직자 토큰으로 `/companies/**` 호출 | **403** `ACCESS_DENIED` | 회원 구분 게이트 — 역할을 보기 전에 걸린다 |
| 기업 토큰으로 구직자 전용 경로 호출 | **403** `ACCESS_DENIED` | 기업 토큰에는 `ROLE_APPLICANT` 가 없다 |
| 구직자가 남의 프로필 조회 | **403** `NOT_OWN_PROFILE` | 존재 확인보다 본인 확인이 먼저 |
| 로그인 실패 (없는 계정 / 비활성 / 비밀번호 불일치) | **401** `INVALID_CREDENTIALS` | 구분하면 계정 열거가 가능해짐 |
| 기업1 계정으로 기업2 공고 조회 | **404** `RESOURCE_NOT_FOUND` | 테넌트 필터가 행을 감춘다. 403 이면 그 번호가 실재한다는 사실이 새어 나간다 |
| VIEWER 의 쓰기 시도 | **403** `ACCESS_DENIED` | 인증은 통과, 역할 권한 부족 |
| 없는 공고 / 지원 건 / 지원자 | **404** `RESOURCE_NOT_FOUND` | 리소스 없음 |
| 중복 지원 / 마감 공고 접수 / 잘못된 전이 | **409** | 리소스의 현재 상태와 충돌 |
| 마스터에 없는 코드 (`"COBOL"` 등) | **400** `UNKNOWN_MASTER_CODE` | 클라이언트 입력 오류 |

---

## 5. 요구사항 3 (가점) — 지원자 상태 변경 이메일 알림

### 5-1. 트리거: 상태 전이 API

요청·응답은 다음과 같습니다.

```
PATCH /api/v1/companies/applications/{applicationId}/stage
Authorization: Bearer {accessToken}
Body : { "transition": "FORWARD", "toStageTypeId": 3, "reason": "최종 면접 통과" }

200 OK
{
  "applicationId": 11, "fromStageTypeId": 2, "fromStageCode": "INTERVIEW",
  "toStageTypeId": 3, "toStageCode": "HIRED", "transition": "FORWARD",
  "stageId": 48, "changedByUserId": 4, "changedBy": "recruiter@company2.com",
  "changedAt": "2026-08-25T14:18:06.05+09:00", "notificationRequested": true
}
```

처리자는 요청 본문이 아니라 **토큰에서** 나오므로 위조할 수 없습니다.
`stage.created_by` 는 `company_user_id` FK 로 저장되어 "누가 했는지"를 DB가 보장하고,
응답의 `changedBy` 는 처리자 이메일입니다.

전이 규칙은 `StageTransitionPolicy` 가 정본이며,
단계 이름·순서·종결 여부를 코드에 하드코딩하지 않고 **`stage_type` 마스터의
`sort_order` / `is_terminal` / `is_passed`** 에서 읽습니다. 중간 단계(예: 코딩테스트)가 추가돼도
데이터만 넣으면 되고 코드는 그대로입니다 — `StageTransitionPolicyTest` 에서 이를 검증합니다.

- `FORWARD` : 진행 라인에서 **정확히 한 단계** 전진 (건너뛰기 → 409)
- `REJECT`  : 진행 중 단계 → 불합격
- `CANCEL`  : **종결 상태 탈출 전용.** 목표 단계는 요청이 아니라 서버가 `stage` 이력에서 역산
- 위반 시 **409 Conflict** + `currentStageTypeId` / `requestedStageTypeId` 동봉

**현행 갱신과 이력 적재는 한 트랜잭션입니다** (Application + Stage = 하나의 Aggregate).
서비스가 두 번 호출하는 게 아니라 **`Application.moveTo()` 한 메서드**가 현재 단계 필드 갱신과 이력
1행 추가를 함께 수행합니다 — 이력 없이 단계만 바뀌는 경로가 코드에 존재하지 않습니다.

동시 전이는 대상 지원 건을 **행 잠금(`SELECT ... FOR UPDATE`)** 으로 읽어 직렬화합니다. 두 담당자가
같은 지원 건을 동시에 옮기면 "면접→합격"과 "면접→불합격"이 겹쳐 이력이 어긋나기 때문입니다.
낙관적 잠금(`@Version`)을 쓰려면 제출 스키마에 버전 컬럼을 추가해야 해서, **DDL 변경이 필요 없는
쪽**을 택했습니다.

### 5-2. 알림 발송

```
StageTransitionService  ──publishEvent──▶  StageChangedEvent
                                              │  @TransactionalEventListener(AFTER_COMMIT)
                                              │  @Async (ats-notify-* 스레드풀)
                                              ▼
                                        StageChangedEmailListener
                                              │
                                     EmailSender (포트)
                                       ├─ SmtpEmailSender    (ainjob.mail.enabled=true)
                                       └─ LoggingEmailSender (false 또는 미지정, DRY-RUN 로그)
```

- **AFTER_COMMIT** — 롤백된 전이에 대해서는 메일이 나가지 않습니다.
- **@Async + 예외 격리** — 메일 서버 장애가 상태 전이 트랜잭션을 되돌리지 않습니다.
- **전용 스레드풀(`ats-notify-*`)** — `@Async("atsNotifyExecutor")` 로 풀을 이름으로 지정합니다.
  한정자를 비우면 컨텍스트 기본 실행기로 가는데, 그러면 MVC 비동기 요청 처리나 나중에 추가될 다른
  `@Async` 작업과 스레드를 나눠 쓰게 됩니다. SMTP 타임아웃이 5초라 메일 서버가 느려지면 무관한
  작업까지 밀리므로 풀을 갈라 두었습니다. 큐(200)까지 차면 **버립니다**(`DiscardPolicy`) —
  알림은 늦느니 포기하는 편이 낫고, 기본값(예외)은 커밋 직후 이벤트 발행 지점으로 새어 나갑니다.
  범용 실행기(`applicationTaskExecutor`)는 부트 빌더로 그대로 살려 둡니다 — `Executor` 빈이
  하나라도 있으면 부트 자동설정이 물러나기 때문에, 알림 풀만 두면 MVC 비동기가 실행기 없이 남습니다.
  두 빈의 공존은 [`AsyncConfigTest`](src/test/java/com/ainjob/ats/config/AsyncConfigTest.java) 가 고정합니다.
- **EmailSender 포트 분리** — AWS SES로 바꿀 때 구현체 하나만 추가하면 되고 도메인 코드는 손대지 않습니다.

DRY-RUN 출력 예시:

```
[MAIL:DRY-RUN] to=h.json248@gmail.com subject=[기업2] 기업2 백엔드 개발자 채용 전형 상태가 '최종합격'(으)로 변경되었습니다.
문지후님, 안녕하세요.

지원하신 전형의 진행 상태가 변경되었습니다.

- 기업     : 기업2
- 공고     : 기업2 백엔드 개발자 채용
- 변경 전  : 면접
- 변경 후  : 최종합격
- 변경 일시: 2026-08-25 13:44
- 안내     : 최종 면접 통과
```

### 5-3. 실제 SMTP로 발송하려면

`application.yml` 에 SMTP 접속 정보를 넣고 `ainjob.mail.enabled: true` 로 켭니다.

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: {계정}
    password: {앱 비밀번호}
    properties:
      mail:
        smtp:
          auth: true
          starttls: { enable: true }
          # 외부 지연이 알림 스레드를 오래 잡지 않게 짧게 끊는다
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
ainjob:
  mail:
    enabled: true          # ← SmtpEmailSender 활성화
```

**발신자 주소는 따로 설정하지 않습니다.** `spring.mail.username`(SMTP 인증 계정)이 그대로 From 이
됩니다. 인증 계정과 다른 From 을 실으면 메일 서버가 사칭으로 보고 거부하거나(Gmail 은 553)
조용히 인증 계정 주소로 바꿔 버리기 때문입니다. 설정 항목으로 열어 두면 "설정한 값과 실제 발신자가
다른" 상태만 생깁니다. `enabled: true` 인데 `spring.mail.username` 이 비어 있으면 **기동 시점에**
막습니다.

`enabled: false` 로 두면 메일 서버 없이도 **알림 흐름 자체(이벤트 → 발송)** 를 콘솔에서 확인할 수
있습니다. SMTP 계정을 준비하기 어려운 환경에서는 이 모드로 두십시오.

### 5-4. 실제 발송 확인 (더미 데이터 기준)

더미 구직자 14명 중 **`h.json248@gmail.com`(applicant_id=7, 문지후) 한 명만 실제 수신 가능한
주소**입니다. 나머지 13명은 수신함이 없는 가짜 주소라 발송 결과를 눈으로 확인할 수 없어, 이메일
알림을 실제로 검증하기 위해 한 자리를 실주소로 뒀습니다.

전이 대상은 **`application_id = 11`** 입니다 — 더미 15건 중 유일하게 종결이 아닌(면접) 지원 건이라
`FORWARD` 를 그대로 실행할 수 있습니다. 나머지 14건은 모두 최종합격(종결)이어서 `FORWARD` 가
409 로 막힙니다. 소속은 기업2이므로 **기업2 담당자 토큰**이 필요합니다.

```bash
# ① 기업2 담당자 로그인
curl -s -X POST http://localhost:8080/api/v1/auth/companies/login   -H "Content-Type: application/json"   -d '{"email":"recruiter@company2.com","password":"ainjob1234!"}'

# ② 면접 → 최종합격. 커밋 직후 h.json248@gmail.com 으로 메일이 나간다
curl -s -X PATCH http://localhost:8080/api/v1/companies/applications/11/stage   -H "Authorization: Bearer eyJ..." -H "Content-Type: application/json"   -d '{"transition":"FORWARD","toStageTypeId":3,"reason":"최종 합격을 축하드립니다."}'

# ③ 원상복구 — 최종합격 → 면접. 이때도 메일이 한 통 더 나간다
curl -s -X PATCH http://localhost:8080/api/v1/companies/applications/11/stage   -H "Authorization: Bearer eyJ..." -H "Content-Type: application/json"   -d '{"transition":"CANCEL","reason":"최종합격 처리 취소 — 면접 단계로 복귀합니다."}'
```

발신자는 `spring.mail.username` 에 넣은 SMTP 인증 계정입니다(5-3 참고).

> **성공하면 로그가 조용합니다.** `SmtpEmailSender` 는 발송 성공 시 아무것도 남기지 않고,
> 실패했을 때만 `StageChangedEmailListener` 가 `상태 변경 알림 발송 실패` 를 ERROR 로 남깁니다.
> 그 로그가 없으면 SMTP 전송까지는 성공한 것이고, 실제 도착 여부는 수신함에서 확인합니다.

②를 실행하면 **기업2 / 공고 3 의 합격자 조회 결과가 2명에서 3명(문지후 추가)으로 바뀝니다**
([3-4](#3-4-기대-결과-3케이스)). 요구사항 1의 필터와 요구사항 3의 상태 전이가 같은 데이터를 보고
있음을 한 번에 보여주는 경로라, ③으로 되돌리기 전에 함께 확인하면 좋습니다.

③으로 되돌려도 `stage` 이력에는 전이 2건이 남습니다. 현행 단계(`application.stage_type_id`)만
더미 적재 직후와 같아지며, 이력까지 완전히 지우려면 더미를 다시 적재하십시오.

---

## 6. HR 기본 유스케이스 (요구사항 밖 · 서비스가 굴러가기 위한 최소한)

과제 요구사항 3개는 **읽기와 상태 변경**만 다룹니다. 그대로 두면 데이터를 만드는 경로가 하나도 없어
모든 시연이 `03_AINJOB_dummy.sql` 에 의존하고, 무엇보다 **`application`(현행) + `stage`(이력)를 한
Aggregate로 묶는다는 설계가 전이 시점에만 지켜집니다.** 초기 `APPLIED` 이력이 존재하는 이유가
"더미 SQL이 넣어줘서"이지 코드가 보장해서가 아니었습니다.

그래서 앞단 세 유스케이스를 채웠습니다. 명세는 아래 표와 같습니다.

**구직자 쪽 (공개 · `/companies` 접두어 없음)**

| API | 인가 | 하는 일 |
|---|---|---|
| `POST /api/v1/applicants` | 비인증 | 회원가입 — 계정 + 프로필 + 학력 + 경력 + 경력별 스킬을 한 트랜잭션으로 저장 |
| `GET /api/v1/job-postings` | 비인증 | 모집 중인 전체 공고. 여러 기업이 섞인다 — 지원 흐름의 시작점 |
| `GET /api/v1/job-postings/{id}` | 비인증 | 공고 상세 + 요구조건. 마감이면 404 |
| `GET /api/v1/applicants/{id}` | 구직자 본인 | 상세 + **직무별 경력연수**(합격자 필터 SQL과 같은 방식으로 서버가 계산) |
| `POST /api/v1/job-postings/{id}/applications` | 구직자 | **지원 — 지원 건과 초기 단계 이력을 함께 생성** |

**기업 쪽 (`/companies` 접두어)**

| API | 인가 | 하는 일 |
|---|---|---|
| `POST /api/v1/companies/job-postings` | OWNER·RECRUITER | 공고 + 요구 스킬/경력/학력을 한 트랜잭션으로 저장 |
| `GET /api/v1/companies/job-postings` · `/{id}` | 기업 전 역할 | 내 회사 공고 목록 / 상세. **마감된 것도 보인다** |
| `PATCH /api/v1/companies/job-postings/{id}/close` | OWNER·RECRUITER | 마감. 재마감은 409 |
| `GET /api/v1/companies/job-postings/{id}/applicants/{id}` | 기업 전 역할 | 내 공고에 지원한 지원자 상세 |

공고 조회가 두 벌인 것은 **보이는 범위가 다르기 때문**입니다. 공개 경로는 모집 중인 공고만
회사를 가리지 않고 보여 주고, 기업 경로는 자기 회사 것만 보여 주되 마감된 공고도 포함합니다 —
담당자에게는 지난 공고도 관리 대상입니다.

### 6-1. "모집 중"의 판정 — 플래그만으로는 부족하다

`is_open` 플래그 하나로 판정하면 **`close_dt` 가 지나도 담당자가 `PATCH /close` 를 부르지 않는 한
공고가 계속 모집 중입니다.** 마감일이 한참 지난 공고가 공개 목록에 뜨고 지원까지 접수됩니다 —
데이터와 도메인 규칙이 어긋난 상태입니다. 그래서 판정 시점에 모집 기간을 함께 봅니다.

```
is_open = 1
AND (open_dt  IS NULL OR open_dt  <= now)   -- 아직 시작 전이면 노출하지 않는다 (예약 공고)
AND (close_dt IS NULL OR close_dt >  now)   -- 마감일이 지났으면 더 받지 않는다
```

정본은 [`JobPosting.isOpenAt(now)`](src/main/java/com/ainjob/ats/domain/JobPosting.java) 이고,
목록 조회는 같은 조건을 SQL 로 싣습니다([`JobPostingRepository`](src/main/java/com/ainjob/ats/jobposting/JobPostingRepository.java)).
**플래그만 읽는 접근자(`isOpen()`)는 없앴습니다** — 남겨 두면 기간을 빠뜨린 채 호출하는 경로가
생기고, 그게 정확히 지금 닫은 구멍이기 때문입니다. 조건이 적용되는 곳은 세 군데입니다.

| 경로 | 동작 |
|---|---|
| `GET /job-postings` (공개 목록) | 기간이 끝났거나 아직 시작 전인 공고는 나오지 않음 |
| `GET /job-postings/{id}` (공개 상세) | 같은 조건으로 **404**. 목록에서 사라진 것과 상세에서 사라진 것이 일치 |
| `POST /job-postings/{id}/applications` (지원) | **409** `JOB_POSTING_CLOSED` |

담당자에게는 그대로 보입니다. 기업 상세 응답의 `open` 은 저장된 플래그가 아니라 **기간까지 반영한
실효 상태**이고, 목록의 `?open=true|false` 도 같은 규칙으로 갈립니다(두 쿼리가 정확한 여집합입니다).

`PATCH /close` 는 기간을 보지 않고 플래그만 봅니다. 기간이 끝난 공고를 담당자가 마감하는 것은
정상적인 조치이기 때문입니다 — 판정으로만 감추고 있던 상태를 컬럼에 실제로 반영하는 셈입니다.

> **실제 서비스라면 스케줄러를 두는 편이 낫습니다.** 위 조건은 조회 시점에 감추기만 할 뿐
> `is_open` 컬럼은 여전히 1 이라, 이 로직을 타지 않는 경로(배치, 통계 쿼리, 관리자 도구, 다른
> 서비스의 직접 조회)에서는 그대로 "모집 중"으로 보입니다. `@Scheduled` 로
> `close_dt < now AND is_open = 1` 인 행을 주기적으로 0 으로 내려 **상태를 컬럼에 확정**하고,
> 위 기간 조건은 스케줄러가 아직 돌지 않은 짧은 구간을 메우는 안전망으로 남기는 구성이 맞습니다.
> 마감 시 지원자에게 알림을 보내야 한다면 어차피 배치가 필요하기도 합니다.
> **과제 범위에서는 조건까지만 넣었습니다** — 스케줄러는 실행 환경(단일 인스턴스 가정, 다중
> 인스턴스면 잠금 필요)까지 얽혀서 과제의 평가 축과 멀어집니다.

지원 API 는 **요청 본문에 지원자 식별자가 없습니다.** 공고는 경로에, 지원자는 토큰에 있으므로
남는 것은 메모뿐입니다. 대리 지원은 검증으로 막는 것이 아니라 **표현할 수 없습니다.** 테넌트도
요청에서 받지 않고 공고 주인에서 나옵니다 — 지원자는 글로벌 풀이고 공고는 기업 소유이므로,
지원 건의 소속 기업은 물어볼 것도 없이 공고 주인입니다.

단계를 바꾸는 방법은 `Application.moveTo()` 하나뿐이고 그 메서드가 현행 갱신과 이력 추가를
**같이** 하므로, 이력 없이 단계만 바뀐 상태가 만들어질 수 없습니다.

이것들이 생기면서 통합 테스트가 **더미 데이터 없이** 전 과정을 재현할 수 있게 됐습니다 —
회원가입 → 공고 열람 → 지원 → 전이 2회 → 합격자 필터에서 조회
(`AtsApiIntegrationTest.endToEndHiringFlow`). 그 흐름에서 **주체가 도중에 바뀌는 것**에 주목하십시오:
회원가입과 지원은 구직자가, 공고 등록과 전형 진행은 담당자가 합니다. 한 토큰으로는 끝까지 갈 수
없습니다. 마지막 단계에서 **과제 원본 SQL이 JPA로 쓴 데이터를 그대로 읽어내는 것**이 JPA 전환의
검증 기준입니다.

---

## 7. 멀티테넌시 격리 — 3계층 횡단 방어

설계 단계에서 정한 격리 ①②③ 이 코드에서 어디에 해당하는지입니다.

| 계층 | 위치 | 하는 일 |
|---|---|---|
| ⓪ 인증 | [`AuthService`](src/main/java/com/ainjob/ats/auth/AuthService.java) · `company_user` / `applicant` | 로그인 성공 시 **계정 행의** `company_id` 와 **어느 테이블에서 찾았는지**(`member_type`)를 토큰에 봉인. 사칭 불가 |
| ① Presentation | [`SecurityConfig`](src/main/java/com/ainjob/ats/auth/SecurityConfig.java) · [`TenantContext`](src/main/java/com/ainjob/ats/tenant/TenantContext.java) · [`ApplicantContext`](src/main/java/com/ainjob/ats/auth/ApplicantContext.java) | 토큰 검증 + 회원 구분 + 역할 인가. 실패 시 필터 체인에서 401·403 종료 |
| ② Persistence **(1차·자동)** | [`TenantFilterAspect`](src/main/java/com/ainjob/ats/tenant/TenantFilterAspect.java) · [`TenantScopeFilter`](src/main/java/com/ainjob/ats/tenant/TenantScopeFilter.java) | 기업 영역 요청이면 Hibernate 필터를 켜서 **모든 JPQL·컬렉션 로딩에 `company_id` 조건을 자동으로** 붙인다 |
| ② Persistence **(2차·명시)** | 서비스의 `isOwnedBy(companyId)` · 네이티브 SQL 의 `:companyId` | 필터가 닿지 않는 세 경로(네이티브 SQL · PK 조회 · 요청 스레드 밖)를 받는다. 여기까지 도달했다는 것은 격리 결함이므로 ERROR 로그를 남긴다 |
| ③ Database | `03_AINJOB_schema.sql` | `uq_application_tenant (company_id, job_posting_id, applicant_id)` — 선두 `company_id` 복합 인덱스 겸 중복 지원 방지 |

`Applicant` 는 글로벌 풀(`company_id` 없음)이므로 문지후가 기업1·기업2에 동시 지원할 수 있고,
격리 기준점은 `Application` 입니다.

### 7-1. 격리 ② 는 어떻게 "자동"인가

`JobPosting` 과 `Application` 에 Hibernate 필터를 걸어 두고, 요청이 기업 담당자 영역일 때만 켭니다.

```
TenantScopeFilter (서블릿)                 /api/v1/companies/** 이면 스코프를 연다
  └ 트랜잭션 어드바이저 (order 0)           영속성 컨텍스트가 열리고
      └ TenantFilterAspect (order 1)       그 안에서 Hibernate 필터를 켠다
          └ 서비스 · 리포지토리             이후 모든 JPQL 에 AND company_id = ? 가 붙는다
```

**활성화 기준이 "기업 회원 토큰인가"가 아니라 "기업 영역 경로인가"** 라는 점이 핵심입니다.
토큰으로 판단하면 기업 담당자가 로그인한 채로 공개 공고 목록을 볼 때 자기 회사 공고만 나옵니다 —
공개 게시판이 텅 비는 장애입니다. 구직자의 지원 접수도 남의 회사 공고를 읽어야 하므로 마찬가지고요.
`TenantFilterIntegrationTest` 가 이 결정을 회귀 테스트로 고정합니다.

#### 자동 주입이 닿지 않는 세 곳

| 경로 | 이유 | 대응 |
|---|---|---|
| 네이티브 SQL | Hibernate 필터가 원천적으로 적용되지 않음 | 과제 원본 SQL 이 `:companyId` 를 직접 바인딩 |
| PK 직접 조회 (`find` / `getReference`) | JPA 명세상 식별자 조회는 필터 대상이 아님 | 기업 영역은 `findById` 대신 `findByIdInTenantScope` (JPQL) 사용 |
| 요청 스레드 밖 (`@Async` 등) | ThreadLocal 스코프가 전파되지 않음 | 현재 알림 리스너는 DB 를 조회하지 않음 |

두 번째 항목이 이 방식의 가장 큰 함정입니다. 필터를 걸어 놓고 `findById` 로 읽으면 **필터가 켜져
있는데도 남의 회사 행이 그대로 반환됩니다.** "자동이니까 안심"이 오히려 위험해지는 지점이라,
`TenantFilterIntegrationTest.findByIdBypassesFilterOnPurpose` 가 이 동작을 명시적으로 고정해
두었습니다.

그래서 서비스의 `isOwnedBy(companyId)` 검사를 **없애지 않고 남겼습니다.** 정상 경로라면 필터가
먼저 걸러 내므로 2차 검사는 도달하지 않고, 도달했다면 위 세 구멍 중 하나로 읽었다는 뜻이므로
`GlobalExceptionHandler` 가 ERROR 로그를 남깁니다.

#### 왜 403 이 아니라 404 인가

필터가 행을 아예 감추므로 "남의 것"이 아니라 "없는 것"이 됩니다. 그리고 그게 더 안전합니다 —
403 으로 구분해 주면 식별자를 훑어 남의 회사 공고·지원 건의 존재 여부를, 나아가 대략적인 채용
규모를 셀 수 있습니다. 없는 리소스와 남의 리소스의 응답은 **요청한 식별자를 빼면 완전히 동일**합니다.

"권한 부족(403)"과 "테넌트 불일치(404)"는 다른 개념이라는 점에 유의하세요. VIEWER 의 쓰기 시도나
구직자 토큰의 기업 경로 호출은 여전히 **403** 입니다.

그래서 **지원자 열람에는 별도의 앵커가 필요합니다.** 지원자만으로는 격리 조건을 만들 수 없으므로
기업의 지원자 조회는 공고 하위 경로에 두고 "우리 회사 공고에 지원했는가"를 묻습니다. 그 사실은
`application(company_id, job_posting_id, applicant_id)` 한 행에 있고, 그 조합은 이미 UNIQUE
인덱스라 **별도 인덱스 없이 격리가 성립합니다.**

---

## 8. 프로젝트 구조

```
ats-api/
├── src/main/java/com/ainjob/ats/
│   ├── AtsApiApplication.java
│   ├── domain/       JPA 엔티티 18개 (스키마 테이블과 1:1)
│   │                 Applicant←Education/Career←CareerSkill · JobPosting←요건 3종
│   │                 Application←Stage (Aggregate) · 마스터 6종 · Company/CompanyUser
│   ├── auth/         SecurityConfig(인가 규칙) · JwtConfig · AuthController/Service
│   │                 MemberType(회원 구분) · ApplicantContext(JWT → applicant_id)
│   │                 CompanyUserRepository · ApplicantAccountRepository · 401·403 핸들러
│   ├── tenant/       TenantContext(JWT 클레임 → company_id / company_user_id) · CompanyRepository
│   │                 TenantScopeFilter/TenantScope(기업 영역 판정) · TenantFilterAspect(격리② 자동 주입)
│   │                 TransactionConfig(어드바이저 순서 고정) · TenantFilters(필터 이름 상수)
│   ├── master/       코드→마스터 엔티티 변환(MasterCodeResolver) + 마스터 리포지토리 5종
│   ├── applicant/    회원가입·본인 조회 + 기업의 지원자 열람 + 합격자 필터 API
│   │                 PassedApplicantFinder ← Jpa… / NativeSql… 두 구현 (설정으로 선택)
│   ├── jobposting/   공개 공고 조회(Public…) + 기업 공고 등록·조회·마감(Company…)
│   ├── application/  지원 API (application + 초기 stage 이력을 한 트랜잭션에)
│   ├── stage/        상태 전이 API + StageTransitionPolicy(전이 규칙)
│   ├── notification/ StageChangedEvent · Listener · EmailSender(SMTP / Logging)
│   ├── common/       ErrorCode · ApiErrorResponse · GlobalExceptionHandler
│   └── config/       StageConfig(전이 규칙 조립) · AsyncConfig(알림 스레드풀)
├── config/
│   └── application-local.yml                 ← DB 접속 정보 + SMTP 계정 (커밋 금지 · 메일 전달)
├── src/main/resources/
│   ├── application.yml                       ← 자격증명 없음
│   └── sql/          passed-applicants.sql  ← 과제 SQL 원본
├── src/test/resources/
│   └── application.yml                       ← 테스트 전용 (메인 설정을 통째로 대신함)
└── src/test/java/com/ainjob/ats/
    ├── AtsApiIntegrationTest.java              (실 DB 필요 · 49건)
    ├── TestTokens.java                                ← 토큰 픽스처(구직자 / 기업 담당자)
    ├── applicant/PassedApplicantControllerTest.java   ← 요구사항 2
    ├── applicant/ApplicantControllerTest.java         ← 회원가입 비인증 · 본인만 조회
    ├── applicant/CompanyApplicantControllerTest.java  ← 공고를 앵커로 한 지원자 열람
    ├── applicant/PassedApplicantServiceTest.java      ← 필터 전략 선택
    ├── jobposting/PublicJobPostingControllerTest.java ← 공개 공고 조회
    ├── jobposting/CompanyJobPostingControllerTest.java← 등록/마감 인가
    ├── application/ApplicationControllerTest.java     ← 지원 인가 · 대리 지원 불가
    ├── stage/StageTransitionControllerTest.java       ← 역할 인가
    ├── stage/StageTransitionPolicyTest.java           ← 전이표 + 첫 단계
    ├── notification/StageChangedEmailListenerTest.java
    ├── config/AsyncConfigTest.java                     ← 알림 풀 / 범용 풀 분리 (DB 불필요)
    ├── domain/ApplicantCareerYearsTest.java            ← 경력연수 집계 · 음수 방지 (DB 불필요)
    └── tenant/TenantFilterIntegrationTest.java        ← 테넌트 필터 (실 DB · 12건)
```

패키지는 **엔티티만 `domain/` 에 모으고 나머지는 기능별로 나눈 혼합 구조**입니다. 여러 기능이 같은
엔티티를 공유하기 때문이며(예: `Application` 을 접수와 전이가 함께 다룸), 엔티티를 기능 패키지에
흩으면 소유권이 모호해집니다.

---

## 9. 설계 판단 요약

| 판단 | 이유 |
|---|---|
| 영속 계층은 **Spring Data JPA** | 지원자(프로필→학력/경력→경력별 스킬)와 공고(공고→요구 스킬/경력/학력)는 부모-자식 3계층을 한 트랜잭션에 저장해야 함. JdbcTemplate으로 쓰면 INSERT 순서와 생성키 회수를 서비스가 손으로 관리하게 됨 |
| 합격자 필터를 **두 구현 + 설정 스위치** | 과제 문구가 "SQL 텍스트 그대로"인지 "그 조회 기능"인지 단정 불가. 한쪽만 만들면 다른 해석에서 지적을 받으므로 둘 다 두고 `ainjob.passed-applicant.strategy` 로 전환. 기본값은 실무 기본인 JPA |
| 두 구현의 **동등성을 테스트로 고정** | 구현이 둘이면 "둘이 같은 답을 낸다"가 검증 대상이 됨. 두 Finder를 나란히 주입해 네 케이스에서 결과를 대조(`bothFinderStrategiesAgree`). 설정으로 빈을 하나만 만들지 않은 이유 |
| 네이티브는 `EntityManager` + `.sql` 파일 | `@Query(nativeQuery=true)` 는 컴파일 타임 상수만 받아 SQL을 자바로 옮겨 적어야 함 → 제출물과 실행 쿼리가 갈라짐 |
| `ddl-auto: **validate**` · 마이그레이션 도구 없음 | 스키마 정본은 과제 제출물인 `03_AINJOB_schema.sql`. Hibernate가 DDL을 만들거나 고치지 못하게 막고, 엔티티가 그 스키마와 맞는지 기동 시 검증만 하게 함 |
| **OSIV 끔** (`open-in-view: false`) | 영속성 컨텍스트를 트랜잭션 안에서 닫아 컨트롤러/직렬화 시점의 예측 못 한 지연 로딩을 차단. 응답 DTO는 서비스에서 완성 |
| 전이 동시성은 **비관적 잠금** | `@Version` 컬럼을 넣으려면 제출 스키마의 DDL을 고쳐야 함. DDL 변경이 필요 없는 `SELECT ... FOR UPDATE`(`@Lock`)로 같은 목적을 달성 |
| 단계 변경은 **`Application.moveTo()` 하나로만** | 현행(`application.stage_type_id`) 갱신과 이력(`stage`) 추가를 엔티티 메서드가 함께 수행. 이력 없이 단계만 바뀌는 경로가 코드에 존재하지 않음 |
| 마스터는 **코드로 주고받음** | 클라이언트가 `skill_id=7` 을 알아야 하는 설계는 마스터 재적재 시 깨짐. `"JAVA"` 같은 안정적 코드를 받아 서버가 PK로 변환 |
| **잡포털로 모델링** (ATS 아님) | 지원자가 로그인해 직접 지원하는 구조. ATS(기업 내부 채용관리 툴)라면 담당자만 로그인하고 지원자는 레코드로만 존재하지만, `applicant` 를 글로벌 풀(`company_id` 없음)로 둔 스키마 자체가 이미 포털의 공유 인재풀 모델이었음 |
| 경로 접두어를 **`/companies`** (식별자 없이) | `/companies/{companyId}` 로 두면 남의 회사를 지정할 자리가 생겨 매 요청 토큰 대조가 필요해짐. 접두어를 "토큰의 회사로 스코프된 리소스" 표시로만 쓰면 URL 문법에 그 자리가 없어짐 |
| 인가를 **회원 구분 + 역할 2단** 으로 | 주체가 둘이 되면 "무슨 역할인가"보다 "어느 쪽 회원인가"가 먼저. `member_type` 을 역할과 같은 `ROLE_*` 형식으로 실어 `hasRole` 하나로 두 축을 표현 |
| 구직자 토큰에 **`company_id`·`role` 을 싣지 않음** | 없는 값을 만들어 넣지 않는 것이 곧 격리. 없는 권한은 흉내 낼 수 없으므로 기업 경로를 통과할 방법이 없음 |
| 지원 요청에서 **`applicantId` 를 제거** | 검증으로 막는 것보다 입력을 없애는 편이 강함. 대리 지원이 요청 형식으로 표현될 수 없음 |
| `stage.created_by` **NULL 허용** | 첫 단계는 구직자 본인이 만들므로 채울 `company_user` 가 없음. NULL = 본인 행위, NOT NULL = 담당자 행위. 행위자 자체는 `application.applicant_id` 로 확정됨 |
| 기업의 지원자 열람을 **공고 하위 경로**에 | 지원자는 글로벌 풀이라 그 자체로 테넌트가 없음. 공고를 앵커로 삼으면 `uq_application_tenant` 를 그대로 타면서 격리가 성립 |
| **JWT 로그인 + `company_user` 테이블** | 과제 명세가 JWT를 약속했고, Tenant 도메인 책임에도 "계정·인증 주체"가 포함됨. `company_id` 를 계정 행에서 읽어야 테넌트 사칭이 막힘 |
| 이메일 **전역 UNIQUE** | 로그인이 `{email, password}` 만으로 끝나 `company_id` 를 클라이언트가 지정할 여지가 사라짐 |
| 역할을 **lookup 테이블 + 클레임** 으로 | 문자열 상태값 금지 원칙과 동일한 맥락. 인가 규칙은 `SecurityConfig` 한 곳에 모음 |
| 계정 **하드 삭제 금지** (`is_active`) | `stage.created_by` FK 로 묶여 있어 담당자가 퇴사해도 감사 이력이 남아야 함 |
| 격리②를 **Hibernate 필터로 자동화** | 설계가 약속한 "모든 쿼리에 `WHERE company_id=?` 자동 주입"을 실제로 구현. 새 JPQL 을 짜면서 조건을 빠뜨릴 수 없게 하는 것이 목적 |
| 필터 활성화를 **토큰이 아니라 경로로** 판단 | 토큰으로 켜면 기업 담당자가 공개 공고 목록을 볼 때 자기 회사 것만 나온다. 구직자의 지원 접수도 남의 회사 공고를 읽어야 한다 |
| `isOwnedBy` 검사를 **지우지 않고 유지** | 필터가 닿지 않는 세 경로(네이티브 SQL · PK 조회 · 요청 스레드 밖)가 남는다. 2차 방어가 발동하면 그 자체가 격리 결함 신호라 ERROR 로그를 남김 |
| 교차 테넌트를 **404 로 응답** | 403 은 "그 번호는 실재한다"를 알려 주는 셈. 식별자를 훑어 남의 회사 리소스 존재 여부·채용 규모를 셀 수 있으므로 없는 리소스와 응답을 동일하게 맞춤 |
| 트랜잭션 어드바이저 **순서를 명시** | 부트 기본값(LOWEST_PRECEDENCE)이면 어떤 애스펙트도 트랜잭션 안쪽에 놓을 수 없어 필터를 켤 영속성 컨텍스트가 없음 |
| `company_id` 를 **입력으로 받지 않음** | 클라이언트가 지정할 수 있으면 테넌트 격리가 클라이언트 신뢰에 의존하게 됨. 출처는 검증된 토큰의 클레임 하나뿐 |
| 토큰 없음 **401**, 소유권 불일치 **403** | `company_id` 는 입력이 아니라 인증 결과. 인증 실패와 인가 실패를 분리 |
| 로그인 실패를 **모두 같은 401** 로 | 없는 계정/비활성/비밀번호 불일치를 구분하면 계정 열거가 가능해짐. 응답 시간도 맞춤 |
| 경력 개월수 음수를 **입력이 아니라 계산에서** 막음 | 입사 예정·퇴사 예정 경력을 미리 등록하는 것은 정상 입력이라 `@PastOrPresent` 로 거부하지 않습니다. 대신 "아직 시작하지 않은 경력은 0개월"을 **세 계산 지점 모두**에 넣었습니다. 음수가 생기면 `DIV`(0 방향 절단)와 HQL 부등식(floor 전제)이 갈려 합격자 필터 두 구현의 답이 달라집니다 (3-2) |
| 마스터 코드를 **종류별 IN 조회 한 번**으로 | 예전에는 스킬만 묶고 학위·전공·직무는 항목마다 조회했습니다. 성능보다 **오류 메시지**가 문제였습니다 — 스킬은 틀린 코드를 한 번에 알려주는데 전공은 하나씩 튕겨내 왕복을 반복하게 했습니다. 같은 요청 안에서 필드에 따라 동작이 갈리지 않도록 넷을 같은 형태로 맞췄습니다 |
| 알림 실행기를 **전용 풀로 분리** | `applicationTaskExecutor` 라는 부트 예약 이름을 쓰면 MVC 비동기와 한정자 없는 `@Async` 가 알림 풀을 함께 씁니다. 그렇다고 이름만 바꾸면 `Executor` 빈이 생겨 부트 기본 실행기가 아예 사라집니다. 그래서 **범용 실행기를 부트 빌더로 되살리고** 알림 전용 풀을 따로 둡니다 (5-2) |
| 목록 조회에 **페이지네이션 없음** | **과제 범위상 의도적으로 넣지 않았습니다.** 더미 공고가 4건이고 심사 시나리오가 전건 대조라, 페이지를 끊으면 확인할 것이 늘기만 합니다. 실제 서비스라면 공고 목록 두 벌과 합격자 필터 모두 `Pageable` 을 받아 `Slice`/`Page` 로 돌려줘야 하고, 정렬 키가 이미 PK(`job_posting_id DESC`)라 커서 페이지네이션으로 넘어가기도 쉽습니다. 합격자 필터는 특히 **응답 건수에 상한이 없다**는 점이 실무에서는 그대로 두기 어려운 지점입니다 |
| 모집 중 판정에 **기간 조건** 추가 | `is_open` 플래그만 보면 `close_dt` 가 지나도 담당자가 마감을 누르지 않는 한 계속 모집 중이 됩니다. 판정을 `isOpenAt(now)` 하나로 모으고 플래그 전용 접근자를 없애 빠뜨릴 경로를 지웠습니다. 실제라면 스케줄러로 컬럼을 확정하는 편이 낫습니다 (6-1) |
| 잘못된 전이 **409** | 요청 자체는 유효하고 **리소스의 현재 단계와 충돌**. 현재 단계가 바뀌면 같은 요청이 성공할 수 있으므로 422보다 409 |
| 전이 규칙을 **stage_type 마스터에서 조립** | 단계 추가/변경이 데이터 작업이 되도록. 문자열 상태값 하드코딩 금지 원칙과 동일한 맥락 |
| `stage.created_by` 를 **FK 로 승격** | 문자열 → `company_user_id`. "무결성은 로직이 아니라 DB 제약조건" 원칙 |
| 중복 지원은 **선검사 + DB 제약 이중 방어** | 서비스 선검사로 원인을 분명히 알리고, 동시 요청으로 함께 통과한 경우는 `uq_application_tenant` 가 막은 뒤 같은 409로 변환 |
| 알림 **AFTER_COMMIT + @Async** | 롤백된 전이에 메일이 나가지 않고, 메일 장애가 전이를 되돌리지 않도록 |
| 발신자를 **SMTP 인증 계정으로 고정** | 인증 계정과 다른 From 은 메일 서버가 거부하거나 바꿔 버림. 설정으로 열어 두면 "설정값과 실제 발신자가 다른" 상태만 생김 |
| 통합 테스트 **환경변수 게이트** | DB 없는 환경에서도 `./gradlew build` 가 통과해야 함 |
| 테스트 설정을 **메인과 같은 값으로 통일** | 클래스패스상 테스트 리소스가 메인을 통째로 대신한다. 값이 갈리면 "테스트는 통과하는데 실행하면 다르게 도는" 경로가 생기므로 서명 키·SMTP·알림 스위치를 환경별로 나누지 않음 |

---

## 10. 검증 결과

| 항목 | 결과 |
|---|---|
| 단위/슬라이스 | **87건 통과** |
| 실 DB 통합 | **63건 통과** — MariaDB 12.3 |
| 합계 | **150건 / 실패 0** |

> **먼저 DB 를 다시 적재하십시오.** `applicant` 에 `password_hash` / `is_active` 가 추가되고
> `stage.created_by` 가 NULL 허용으로 바뀌었습니다. 예전 스키마 그대로면 `ddl-auto: validate` 가
> 불일치를 잡아 기동이 실패합니다(1-2절). 더미도 다시 넣어야 합니다 — 공고 `close_dt` 가
> `2027-12-31` 로 바뀌었고, 예전 값(`2025-12-31`)은 이미 지난 날짜라 모집 기간 조건(6-1)에 걸려
> 시연 공고가 하나도 보이지 않습니다.
>
> **그리고 `config/application-local.yml` 이 있어야 합니다**(1-3). 저장소에 자격증명이 없어
> 그 파일 없이는 통합 테스트가 DB 에 붙지 못합니다.

통합 테스트가 실제로 확인하는 것:

- `ddl-auto: validate` — 엔티티 18개가 `03_AINJOB_schema.sql` 과 일치
- 기대 결과 3케이스 재현 (기업1 BE 3명 / 기업2 BE 2명 / 기업2 FE 3명)
- **합격자 필터 두 구현의 결과 동등성** (JPA HQL = 과제 원본 SQL)
- **클레임 → 권한 변환** — 슬라이스 테스트가 권한을 직접 주입하는 것과 달리, 로그인 엔드포인트가
  실제로 발급한 토큰을 헤더에 실어 "구직자 토큰으로 기업 경로 호출 시 403"을 증명
- end-to-end: 회원가입 → 공고 열람 → 지원 → 전이 2회 → 합격자 필터 등장 (주체가 도중에 바뀐다)
- 회원 구분 격리(403 양방향) · 본인 프로필만 조회(403) · 공고를 앵커로 한 지원자 열람(404)
- **테넌트 필터(격리②)** — 기업 영역에서는 남의 회사 공고·지원 건이 404 로 사라지고, 그 응답이
  없는 리소스와 식별자를 빼면 동일하다는 것까지 대조
- **테넌트 필터가 켜지면 안 되는 곳** — 기업 토큰으로 공개 공고 목록을 봐도 전체가 보이고,
  익명 응답과 완전히 같다는 회귀 테스트
- **PK 직접 조회는 필터를 우회한다는 사실** 자체를 고정(그래서 기업 영역이 JPQL 을 쓴다)
- 테넌트 격리(403) · 중복 지원(409) · 마감 공고 지원(409) · 없는 리소스(404)
- **모집 기간 조건(6-1)** — `close_dt` 가 지난 공고가 담당자의 마감 없이도 공개 목록·상세에서
  사라지고 지원이 409 로 막히는지, 담당자에게는 `?open=false` 로 잡히고 `open` 이 false 인지,
  `open_dt` 가 미래인 예약 공고가 아직 공개되지 않는지, 그리고 **기간 안의 공고에는 조건이 과하게
  걸리지 않는지**까지 (5건)
- 구직자가 만든 첫 단계의 `stage.created_by` 가 NULL, 담당자 전이 이력은 계정 FK 로 채워지는지
- 지원·전이 직후 `application.stage_type_id` 와 최신 `stage.stage_type_id` 가 일치하는지 raw SQL 로 대조


---

## 11. 구현하지 않은 것 (과제 범위 밖)

**의도적으로 넣지 않은 것들입니다.** 몰라서 빠진 것과 판단해서 뺀 것을 구분해 두려고 한곳에 모았습니다.
기준은 하나입니다 — **과제의 평가 축(멀티테넌시 격리 / 상태 전이 / 복합 필터 SQL)에 걸리는가.**
걸리지 않으면서 제출물의 부피만 키우는 것은 뺐습니다. 실제 서비스라면 전부 필요합니다.

### 11-1. 운영·배포

| 항목 | 왜 안 넣었나 | 실제라면 |
|---|---|---|
| **API 문서 (OpenAPI/Swagger)** | 엔드포인트가 11개고 이 README 의 표가 요청·응답·인가·상태코드를 전부 담고 있어, 심사에는 문서가 이중이 됩니다 | `springdoc-openapi` 의존성 한 줄 + DTO 어노테이션. 클라이언트가 붙는 순간 **실행 가능한 스펙**이 필요하고, 표는 코드와 갈라지지만 생성된 스펙은 갈라지지 않습니다 |
| **컨테이너 (Dockerfile / compose)** | 실행 단위를 "jar 하나"로 좁히는 편이 심사자에게 단순합니다(1장). Docker 를 전제하면 심사 환경에 런타임을 하나 더 요구하게 됩니다 | `compose.yaml` 로 MariaDB + 앱을 함께 띄우고 스키마·더미를 `initdb.d` 로 자동 적재. "MariaDB 를 준비하고 SQL 두 개를 넣으세요"(1-2)가 통째로 사라집니다 |
| **CI (GitHub Actions 등)** | 저장소를 파일로 전달하는 과제라 파이프라인이 돌 곳이 없습니다 | `./gradlew test` 가 통합까지 함께 돌리므로 **CI 잡에 MariaDB 서비스 컨테이너가 필수**입니다. 스키마·더미를 적재하고 접속 정보를 `config/application-local.yml` 로 주입하는 단계까지 갖춰야 150건이 그대로 돕니다 |
| **모니터링·헬스체크 (Actuator)** | 관측 대상이 없습니다 | `/actuator/health` 를 인가 예외로 열고 메트릭을 수집. 특히 **알림 스레드풀 큐 적재량**은 봐야 합니다(11-3) |
| **구조적 로깅 / 요청 추적 ID** | 단일 인스턴스에 요청 흐름이 짧습니다 | MDC 에 요청 ID·`company_id`·`company_user_id` 를 실어 JSON 으로 남깁니다. 멀티테넌시 서비스에서 "어느 회사 요청이었나"는 사후 추적의 기본 키입니다 |

### 11-2. 인증·계정

**리프레시 토큰, 비밀번호 변경·재설정, 로그인 실패 잠금(brute-force 방어), 로그아웃(토큰 무효화)** 이
없습니다. 액세스 토큰 1시간(`token-ttl: PT1H`) 단일 발급이고, 만료되면 다시 로그인합니다.

토큰을 무효화하려면 상태를 어딘가에 둬야 해서(블랙리스트 / 리프레시 토큰 저장소) 저장소가 하나 늘고,
그게 과제의 평가 축과 무관하게 구조를 키웁니다. 실제라면 리프레시 토큰을 DB 에 두고 액세스 토큰
수명을 5~15분으로 줄이는 것이 기본형입니다.

구직자 계정도 **가입과 로그인까지만** 있습니다 — 프로필 수정·탈퇴, 내 지원 현황 조회는 없습니다.
과제의 평가 축(멀티테넌시 격리 / 상태 전이 / 복합 필터 SQL)에 걸리지 않아 범위 밖으로 두었습니다.

**서명은 대칭키(HS256)입니다.** 발급자와 검증자가 같은 애플리케이션이라 이걸로 충분합니다.
인증 서버가 분리되면 비대칭키(RS256)여야 하는데 — 검증자가 서명 키를 갖고 있으면 토큰을 위조할 수
있기 때문입니다 — 그때는 [`JwtConfig`](src/main/java/com/ainjob/ats/auth/JwtConfig.java) 만
JWK Set URI 방식으로 바꾸면 됩니다. 나머지 코드는 `Jwt` 객체를 그대로 받으므로 손대지 않습니다.

### 11-3. 성능·확장

| 항목 | 현재 | 실제라면 |
|---|---|---|
| **페이지네이션** | 세 목록(공개 공고 / 기업 공고 / **합격자 필터**) 모두 전건 반환 | 전부 `Pageable`. 특히 합격자 필터는 **응답 건수에 상한이 없다**는 점이 그대로 두기 어려운 지점입니다. 정렬 키가 이미 PK 라 커서 페이지네이션으로 넘어가기도 쉽습니다 (9장) |
| **마감 공고 정리 스케줄러** | 조회 시점 조건으로 감추기만 함 | `@Scheduled` 로 `is_open` 컬럼을 확정. 조건은 안전망으로 남깁니다 (6-1) |
| **알림 재발송 (DLQ/배치)** | 발송 실패는 ERROR 로그만 남기고 버립니다 | 발송 이력 테이블 + 재시도 배치. 지금은 "알림 실패가 상태 전이를 되돌리지 않는다"까지만 보장합니다 |
| **마스터 코드 캐싱** | 네 종류 모두 **종류별 IN 조회 한 번**으로 줄였습니다(9장). 다만 요청마다 DB 를 칩니다 | 참조 데이터라 거의 바뀌지 않으므로 `@Cacheable`. 엔티티를 캐시에 담으면 준영속 인스턴스가 여러 영속성 컨텍스트를 넘나들므로 `getReferenceById` 로 다시 붙이는 처리가 필요합니다 |
| **전이 규칙 갱신** | `stage_type` 마스터를 **기동 시 한 번** 읽어 규칙 객체를 만듭니다(`StageConfig`) | 단계를 운영 중 바꾸려면 재기동이 필요합니다. 캐시 무효화 엔드포인트나 주기적 갱신을 둡니다 |

### 11-4. 도메인

- **`stage.created_by` NULL 허용** — "무결성은 DB 제약으로"에서 한 걸음 물러선 지점입니다. 행위자를
  FK 로 온전히 남기려면 `created_by_applicant` 컬럼과 XOR CHECK 제약이 필요하고, 지금은
  `application.applicant_id` 로 역추적하는 것으로 갈음했습니다. NULL = 본인 행위,
  NOT NULL = 담당자 행위라는 규칙 자체는 2-3 의 스키마 변경 이력에 남아 있습니다.
- **학위당 1건 제약** — `uq_education (applicant_id, degree_level_id)` 이라 복수 전공이나 학사 학위
  2개를 표현할 수 없습니다. 과제 스키마를 그대로 둔 결과입니다.
- **공고 수정 API 없음** — 등록과 마감만 있습니다. 요구조건이 바뀌면 합격자 판정 결과가 소급해서
  달라지므로, 수정을 열려면 "이미 진행 중인 전형에 어떻게 반영할 것인가"를 먼저 정해야 합니다.
- **첨부파일(이력서 파일) 없음** — 스토리지가 붙어야 하고 과제 스키마에도 자리가 없습니다.
- **종료일이 미래인 경력은 그대로 셉니다** — `startAt=2020, endAt=2030` 이면 10년으로 계산됩니다
  (재직 중과 같은 취급). 음수가 아니라 두 구현이 갈리지도 않으므로 계산 규칙(3-2)에서는 손대지
  않았습니다. 다만 경력을 부풀릴 수 있는 입력이므로, 실제 서비스라면 종료일을 현재 시각으로
  잘라 세거나 증빙(경력증명서) 확인 절차를 두는 편이 맞습니다.
