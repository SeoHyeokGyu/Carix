# OCI Deployment Plan Documentation Design

## 목적

Carix의 향후 운영 배포 대상이 Oracle Cloud Infrastructure(OCI)임을 프로젝트
문서에 명확히 기록한다.

## 범위

- `README.md`의 `프로젝트 상태` 체크리스트에 미완료 작업을 한 줄 추가한다.
- 항목은 `Oracle Cloud Infrastructure(OCI) 배포 환경 구성`으로 표기한다.
- 인스턴스, 네트워크, 컨테이너, 도메인, HTTPS 등 상세 배포 절차는 작성하지
  않는다.

## 반영 위치

`애플리케이션 구현` 항목 다음에 아래 항목을 배치한다.

```md
- [ ] Oracle Cloud Infrastructure(OCI) 배포 환경 구성
```

## 완료 기준

- README만 읽어도 OCI 배포가 향후 작업임을 알 수 있다.
- 해당 항목은 완료되지 않은 계획으로 표시된다.
- 기존 프로젝트 상태와 기술 스택 설명은 변경하지 않는다.
