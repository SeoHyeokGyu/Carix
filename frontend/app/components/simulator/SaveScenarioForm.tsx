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
 * `시나리오 저장` 팝오버 안의 이름 칸. 열리면 이름 칸에 포커스하고, `저장` 을 누를 때만 저장한다.
 * 성공하면 팝오버가 닫히므로 확인 문장은 라우트가 보인다. 여기서는 실패만 보인다.
 */
export function SaveScenarioForm({ onSave, blocked }: SaveScenarioFormProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    rootRef.current?.querySelector("input")?.focus();
  }, []);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      await onSave(name.trim() === "" ? null : name.trim());
    } catch (failure) {
      setError(describeError(failure));
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
            placeholder="예: A사 오퍼"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        )}
      </Field>
      <div>
        <Button
          type="button"
          className="min-h-11"
          disabled={saving || blocked}
          onClick={submit}
        >
          저장
        </Button>
      </div>
      {error && (
        <p role="alert" className="text-sm text-carix-danger">
          {error}
        </p>
      )}
    </div>
  );
}
