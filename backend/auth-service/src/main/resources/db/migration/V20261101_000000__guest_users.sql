-- 가입 없이 쓰는 임시 사용자(게스트). 이메일·비밀번호 없이 만들고, 가입하면 같은 행에 붙인다.
ALTER TABLE users ADD COLUMN guest BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
-- 정식 사용자는 두 값이 반드시 있다. 게스트 여부와 무관하게 NULL 을 허용하면 로그인 불가 계정이 생긴다.
ALTER TABLE users ADD CONSTRAINT users_account_credentials
    CHECK (guest OR (email IS NOT NULL AND password_hash IS NOT NULL));
-- 정리 작업이 게스트만 훑는다.
CREATE INDEX idx_users_guest_created_at ON users (created_at) WHERE guest;
