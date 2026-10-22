import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { describeError } from "../../lib/simulatorForm";
import { Field } from "../onboarding/Field";

interface SaveScenarioFormProps {
  /** 저장 실패는 던진다 — 이 폼이 문장으로 보인다. */
  onSave: (name: string | null) => Promise<void>;
  /** 입력이 결과와 어긋난 동안(재계산 중 등) 저장을 막는다. */
  blocked: boolean;
}

/** `시나리오 저장`. 누를 때만 저장한다. ②와 결과 화면이 함께 쓴다. */
export function SaveScenarioForm({ onSave, blocked }: SaveScenarioFormProps) {
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(
    null,
  );

  const submit = async () => {
    setSaving(true);
    setMessage(null);
    try {
      await onSave(name.trim() === "" ? null : name.trim());
      setName("");
      setMessage({ ok: true, text: "시나리오를 저장했습니다." });
    } catch (error) {
      setMessage({ ok: false, text: describeError(error) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-2">
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
          시나리오 저장
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
