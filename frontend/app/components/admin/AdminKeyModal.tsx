import { X } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { HEADING } from "@/components/ui/heading";
import { Input } from "@/components/ui/input";

import {
  clearAdminKey,
  getAdminKey,
  setAdminKey,
} from "../../lib/collectorApi";

interface AdminKeyModalProps {
  isOpen: boolean;
  onClose: () => void;
  onKeyUpdated: () => void;
}

export function AdminKeyModal({
  isOpen,
  onClose,
  onKeyUpdated,
}: AdminKeyModalProps) {
  if (!isOpen) return null;

  return <AdminKeyModalContent onClose={onClose} onKeyUpdated={onKeyUpdated} />;
}

function AdminKeyModalContent({
  onClose,
  onKeyUpdated,
}: {
  onClose: () => void;
  onKeyUpdated: () => void;
}) {
  const [keyInput, setKeyInput] = useState(() => getAdminKey());
  const [hasExistingKey, setHasExistingKey] = useState(() =>
    Boolean(getAdminKey()),
  );

  const handleSave = () => {
    setAdminKey(keyInput);
    onKeyUpdated();
    onClose();
  };

  const handleClear = () => {
    clearAdminKey();
    setKeyInput("");
    setHasExistingKey(false);
    onKeyUpdated();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="admin-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4"
    >
      <Card className="w-full max-w-md gap-0 shadow-2xl">
        <div className="flex items-center justify-between pb-3 border-b border-carix-border">
          <h2 id="admin-modal-title" className={HEADING.section}>
            Collector 관리자 시크릿 키 설정
          </h2>
          <Button
            type="button"
            variant="ghost"
            size="icon-xs"
            aria-label="닫기"
            onClick={onClose}
          >
            <X />
          </Button>
        </div>

        <div className="mt-4 space-y-4">
          <p className="text-xs text-carix-text/70 leading-relaxed">
            파이프라인 제어(크롤링, KOSIS 임금 수집, 중복제거, 사전 집계 등)를
            수행하려면 서버에 설정된 관리자 키(
            <code className="rounded bg-carix-border/50 px-1 py-0.5">
              X-Admin-Key
            </code>
            )가 필요합니다. 입력된 키는 브라우저 로컬 저장소에 안전하게
            보존됩니다.
          </p>

          <div>
            <label
              htmlFor="admin-key-input"
              className="block text-xs font-medium text-carix-text mb-1"
            >
              관리자 시크릿 키 (Admin Secret Key)
            </label>
            <Input
              id="admin-key-input"
              type="password"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              placeholder="예: carix-admin-secret-dev-key"
            />
            <p className="mt-1 text-[11px] text-carix-text/50">
              * 로컬 개발 기본값:{" "}
              <code className="text-carix-info">
                carix-admin-secret-dev-key
              </code>
            </p>
          </div>

          <div className="flex items-center justify-between pt-2">
            {hasExistingKey ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleClear}
              >
                저장된 키 삭제
              </Button>
            ) : (
              <span />
            )}

            <div className="flex gap-2">
              <Button type="button" variant="outline" onClick={onClose}>
                취소
              </Button>
              <Button type="button" onClick={handleSave}>
                저장하기
              </Button>
            </div>
          </div>
        </div>
      </Card>
    </div>
  );
}
