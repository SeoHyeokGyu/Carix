-- 인증을 이메일·비밀번호 계정으로 바꾼다 (SECURITY.md §1, technical-specification §1.2).
--
-- invitation·magic_link_token 은 여기서 지우지 않는다. 그 테이블을 읽는 코드가 아직 살아
-- 있어서 지금 드롭하면 런타임에 터진다. 코드 폐기 단계에서 함께 지운다.

-- 매직링크로 만들어진 기존 행은 비밀번호가 없어 새 방식으로는 로그인할 수 없는 죽은 데이터다.
-- 그대로 두면 email 이 UNIQUE 라서 같은 주소로 가입할 때 이미 사용 중이라며 막힌다 —
-- 로그인도 안 되고 가입도 안 되는 유령 계정이 된다.
-- 어제 로그인 경로 검증용으로 만든 계정 하나뿐이고, core 스키마에 딸린 데이터가 없다
-- (career_profile·income_history·expense_history·user_skill·index_snapshot·career_scenario 전부 0행).
-- session·magic_link_token 은 ON DELETE CASCADE 로 함께 사라진다.
DELETE FROM users;

-- BCrypt 해시는 60자 고정. 알고리즘 교체 여지를 두고 100 으로 잡는다.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- password_hash 는 NULL 허용으로 둔다. 최종 설계는 NOT NULL 이지만, 아직 살아 있는
-- UserService.findOrCreate 가 매직링크 요청 시 password_hash 없는 행을 넣기 때문에
-- 지금 걸면 매직링크 발송이 500 을 낸다. 그 코드가 사라지는 폐기 단계에서 NOT NULL 로
-- 조인다.

-- 세션 토큰도 원본이 아니라 해시로 저장한다. 세션은 30일짜리 자격증명이라 DB 유출 시
-- 피해가 가장 크다. 위 DELETE 로 기존 행이 사라졌으므로 값 변환은 필요 없다.
ALTER TABLE session RENAME COLUMN session_token TO token_hash;
