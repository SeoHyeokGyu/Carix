import { useState } from "react";
import {
  getAdminKey,
  setAdminKey,
  clearAdminKey,
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
      <div className="w-full max-w-md rounded-xl border border-carix-border bg-carix-surface p-6 shadow-2xl transition-all">
        <div className="flex items-center justify-between pb-3 border-b border-carix-border">
          <div className="flex items-center gap-2">
            <span className="text-xl">🔑</span>
            <h3
              id="admin-modal-title"
              className="text-base font-semibold text-carix-text"
            >
              Collector 관리자 시크릿 키 설정
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-carix-text/50 hover:text-carix-text rounded-md p-1 text-sm transition-colors"
          >
            ✕
          </button>
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
            <input
              id="admin-key-input"
              type="password"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              placeholder="예: carix-admin-secret-dev-key"
              className="w-full rounded-md border border-carix-border bg-carix-bg px-3 py-2 text-sm text-carix-text outline-hidden focus:border-carix-info focus:ring-1 focus:ring-carix-info"
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
              <button
                type="button"
                onClick={handleClear}
                className="text-xs text-carix-danger hover:underline"
              >
                저장된 키 삭제
              </button>
            ) : (
              <span />
            )}

            <div className="flex gap-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-md border border-carix-border px-3 py-1.5 text-xs font-medium text-carix-text/80 hover:bg-carix-border/30 transition-colors"
              >
                취소
              </button>
              <button
                type="button"
                onClick={handleSave}
                className="rounded-md bg-carix-info px-4 py-1.5 text-xs font-medium text-white hover:opacity-90 transition-opacity"
              >
                저장하기
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
