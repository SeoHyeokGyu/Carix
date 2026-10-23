import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";

export type RecordSaveStatus = "idle" | "saving" | "saved" | "failed";

/** 같은 화면의 여러 기록 저장이 함께 쓰는 시작·끝 알림. */
export interface RecordSaveTracker {
  begin(): void;
  end(succeeded: boolean): void;
}

/**
 * 마이페이지 기록(상여 조건·입사일)을 고르는 즉시 저장한다.
 * 저장은 하나씩 차례로 보낸다 — 앞선 요청이 늦게 끝나 새 답을 덮지 않게.
 * 상태 문장은 마지막 요청의 결과만 따른다.
 */
export function useRecordSave<P>(
  save: (payload: P) => Promise<unknown>,
  tracker: RecordSaveTracker,
  saved: P | null,
) {
  const [status, setStatus] = useState<RecordSaveStatus>("idle");
  const chain = useRef<Promise<void>>(Promise.resolve());
  const seq = useRef(0);
  const lastRequested = useRef<{ key: string; payload: P } | null>(
    saved === null ? null : { key: JSON.stringify(saved), payload: saved },
  );

  const run = (payload: P) => {
    const ticket = ++seq.current;
    lastRequested.current = { key: JSON.stringify(payload), payload };
    setStatus("saving");
    tracker.begin();
    chain.current = chain.current.then(async () => {
      let succeeded = false;
      try {
        await save(payload);
        succeeded = true;
      } catch {
        // 실패는 상태 문장과 다시 시도로 보인다. 결과는 이전 기록 기준으로 남는다.
      }
      if (ticket === seq.current) setStatus(succeeded ? "saved" : "failed");
      tracker.end(succeeded);
    });
  };

  /** 마지막으로 보낸 값과 같고 실패하지 않았으면 다시 보내지 않는다. */
  const submit = (payload: P) => {
    if (
      lastRequested.current?.key === JSON.stringify(payload) &&
      status !== "failed"
    ) {
      return;
    }
    run(payload);
  };

  const retry = () => {
    if (lastRequested.current) run(lastRequested.current.payload);
  };

  return { status, submit, retry };
}

interface RecordSaveNoteProps {
  status: RecordSaveStatus;
  /** "소득 기록" / "프로필" */
  target: string;
  /** 아직 저장하지 않았을 때의 안내. 없으면 아무것도 그리지 않는다. */
  idleNote?: string;
  /** 실패 때 결과가 어느 값 기준인지 덧붙이는 문장. */
  staleNote?: string;
  onRetry: () => void;
}

export function RecordSaveNote({
  status,
  target,
  idleNote,
  staleNote,
  onRetry,
}: RecordSaveNoteProps) {
  const text =
    status === "saving"
      ? "저장 중…"
      : status === "saved"
        ? `✓ ${target}에 저장됨`
        : idleNote;
  return (
    <span aria-live="polite" className="text-xs font-normal">
      {status === "failed" ? (
        <span className="text-carix-danger">
          <span>{target}에 저장하지 못했습니다.</span>
          {staleNote && <span> {staleNote}</span>}{" "}
          <Button type="button" variant="link" size="xs" onClick={onRetry}>
            다시 시도
          </Button>
        </span>
      ) : (
        text && <span className="text-muted-foreground">{text}</span>
      )}
    </span>
  );
}
