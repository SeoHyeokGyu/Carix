# Wireframes

[wiremd](https://github.com/teezeit/wiremd) 마크다운 문법으로 작성, git엔 `.md` 소스만 관리.

## 기본 스타일: `tailwind`

이 프로젝트는 Tailwind 기반이라 실제 룩에 가장 근접한 `tailwind` 스타일을 기본으로 채택.

```bash
wiremd <file>.md --style tailwind -o <file>.html
```

렌더된 `.html`은 로컬 확인용 산출물이라 git에 커밋하지 않음(`.gitignore` 처리).
