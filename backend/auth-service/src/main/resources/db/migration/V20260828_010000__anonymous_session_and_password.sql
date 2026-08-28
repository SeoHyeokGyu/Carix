-- 인증을 익명 세션 우선 + 비밀번호 승격으로 바꾼다 (SECURITY.md §1, technical-specification §1.2).
--
-- invitation·magic_link_token 은 여기서 지우지 않는다. 그 테이블을 읽는 코드가 아직 살아
-- 있어서 지금 드롭하면 런타임에 터진다. 코드 폐기 단계에서 함께 지운다.

-- 어제 로그인 경로 검증용으로 만든 계정 하나뿐이고, core 스키마에 딸린 데이터가 없다
-- (career_profile·income_history·expense_history·user_skill·index_snapshot·career_scenario 전부 0행).
-- 아래 CHECK 제약이 email 만 있고 password_hash 가 없는 이 행을 거부하므로 먼저 지운다.
-- session·magic_link_token 은 ON DELETE CASCADE 로 함께 사라진다.
DELETE FROM users;

-- 익명 사용자는 이메일이 없다. UNIQUE 는 NULL 을 여럿 허용하므로 익명이 아무리 많아도
-- 충돌하지 않는다.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- BCrypt 해시는 60자 고정. 알고리즘 교체 여지를 두고 100 으로 잡는다.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- 반쯤 승격된 행(email 만 있고 password_hash 가 없는 행)을 막는 CHECK 제약은 여기 넣지
-- 않는다. 아직 살아 있는 UserService.findOrCreate 가 매직링크 요청 시 정확히 그 모양의
-- 행을 넣기 때문에, 지금 걸면 매직링크 발송이 제약 위반으로 500 을 낸다.
-- 그 코드가 사라지는 폐기 단계에서 함께 건다. 그때까지 불변식은 User.anonymous()/promote()
-- 가 코드로 지킨다.

-- 세션 토큰도 원본이 아니라 해시로 저장한다. 세션은 30일짜리 자격증명이라 DB 유출 시
-- 피해가 가장 크다. 위 DELETE 로 기존 행이 사라졌으므로 값 변환은 필요 없다.
ALTER TABLE session RENAME COLUMN session_token TO token_hash;
