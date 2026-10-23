import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { describeError } from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";

interface SaveScenarioFormProps {
  /**
   * 저장 실패는 던진다 — 이 폼이 문장으로 보인다. 입력이 무효라 저장하지 않았으면 false
   * (호출자가 이미 오류 칸을 보였으므로 이 폼은 아무 문장도 띄우지 않는다).
   */
  onSave: (name: string | null) => Promise<boolean>;
  /** 입력이 결과와 어긋난 동안(재계산 중 등) 저장을 막는다. */
  blocked: boolean;
}

/**
 * 이동 줄의 저장 버튼을 누르면 펼쳐지는 이름 칸. 펼치면 이름 칸에 포커스하고, `저장` 을 누를 때만 저장한다.
 * ① 과 ② 가 함께 쓴다.
 */
export function SaveScenarioForm({ onSave, blocked }: SaveScenarioFormProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    rootRef.current?.querySelector("input")?.focus();
  }, []);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(
    null,
  );

  const submit = async () => {
    setSaving(true);
    setMessage(null);
    try {
      const saved = await onSave(name.trim() === "" ? null : name.trim());
      if (!saved) return;
      setName("");
      setMessage({ ok: true, text: "시나리오를 저장했습니다." });
    } catch (error) {
      setMessage({ ok: false, text: describeError(error) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div ref={rootRef} className="flex flex-col gap-2">
      <Field id="scenarioName" label="시나리오 이름 (선택)">
        {(aria) => (
          <Input
            {...aria}
            type="text"
            placeholder="예: A사 오퍼 제안"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        )}
      </Field>
      <div>
        <Button type="button" disabled={saving || blocked} onClick={submit}>
          저장
        </Button>
      </div>
      {message && (
        <p
          role={message.ok ? "status" : "alert"}
          className={message.ok ? "text-sm" : "text-sm text-carix-danger"}
        >
          {message.text}
        </p>
      )}
    </div>
  );
}
