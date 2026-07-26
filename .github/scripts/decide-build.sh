#!/usr/bin/env bash
#
# 이 컴포넌트의 이미지를 새로 빌드해야 하는지 판정한다.
#
# 배포는 compose의 IMAGE_TAG 하나로 모든 서비스를 가리키므로, 안 바뀐 컴포넌트도
# 이번 커밋 SHA 태그를 반드시 가져야 한다. 다만 그 태그는 빌드가 아니라 재태깅으로도
# 만들 수 있다. 이 스크립트는 둘 중 무엇을 할지 정한다.
#
# 기준점은 `:latest` 이미지에 박힌 org.opencontainers.image.revision 라벨이다.
# `:latest` 는 항상 "가장 최근에 빌드된 이미지"이므로, 그 라벨이 곧 재태깅의 원본이 된다.
# github.event.before 를 쓰지 않는 이유는 그것이 "직전 푸시"일 뿐 "직전에 성공한 빌드"가
# 아니기 때문이다. 앞선 실행에서 이 잡이 실패했다면 before 기준 diff는 변경을 놓친다.
#
# 판정할 수 없는 경우는 전부 빌드로 떨어진다. 최악이 현행 유지라 안전하다.
#
# 입력: COMPONENT(backend|frontend), IMAGE_BASE, GITHUB_SHA, GITHUB_OUTPUT, GITHUB_EVENT_NAME
set -euo pipefail

: "${COMPONENT:?COMPONENT 미지정}"
: "${IMAGE_BASE:?IMAGE_BASE 미지정}"
: "${GITHUB_SHA:?GITHUB_SHA 미지정}"

REPO="${IMAGE_BASE}/${COMPONENT}"

decide() {
	echo "$2"
	echo "build=$1" >> "${GITHUB_OUTPUT:-/dev/stdout}"
	exit 0
}

# 수동 실행은 강제 재빌드로 본다. 사람이 돌리는 경우는 대개 뭔가 다시 만들고 싶을 때다.
if [ "${GITHUB_EVENT_NAME:-push}" = "workflow_dispatch" ]; then
	decide true "workflow_dispatch — 강제 빌드"
fi

LAST_REV=$(docker buildx imagetools inspect "${REPO}:latest" \
	--format '{{ index .Image.Config.Labels "org.opencontainers.image.revision" }}' 2>/dev/null || true)

case "$LAST_REV" in
	"" | "<no value>" | "<nil>")
		decide true "${REPO}:latest 가 없거나 revision 라벨이 없다 — 빌드"
		;;
esac

if ! git cat-file -e "${LAST_REV}^{commit}" 2>/dev/null; then
	decide true "이전 revision(${LAST_REV})이 히스토리에 없다 — 빌드"
fi

# 워크플로 자체가 바뀌면 빌드 방식이 바뀐 것일 수 있으므로 다시 만든다.
if ! git diff --quiet "$LAST_REV" "$GITHUB_SHA" -- \
	"${COMPONENT}/" .github/workflows/deploy.yml .github/scripts/decide-build.sh; then
	decide true "${LAST_REV::7}..${GITHUB_SHA::7} 에서 ${COMPONENT}/ 변경 있음 — 빌드"
fi

decide false "${LAST_REV::7}..${GITHUB_SHA::7} 에서 ${COMPONENT}/ 변경 없음 — 재태깅"
