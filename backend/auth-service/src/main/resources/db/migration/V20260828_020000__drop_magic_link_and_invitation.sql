-- 매직링크·초대 잔재를 걷는다. 그 테이블을 읽던 코드가 이 배포에서 함께 사라진다.

DROP TABLE magic_link_token;
DROP TABLE invitation;

-- 이제 password_hash 없는 행을 만드는 경로가 없다. 최종 설계대로 조인다
-- (technical-specification §1.2). 앞 마이그레이션이 기존 행을 지웠고 그 뒤로 계정을
-- 만드는 경로는 회원가입뿐이라 항상 값이 있다.
ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
