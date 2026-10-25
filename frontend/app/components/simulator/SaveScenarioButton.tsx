import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";

import { SaveScenarioForm } from "./SaveScenarioForm";

interface SaveScenarioButtonProps {
  /** false 면 열지 않는다 — 호출자가 첫 오류 칸으로 포커스를 옮긴다. */
  onOpen: () => boolean;
  onSave: (name: string | null) => Promise<boolean>;
  blocked: boolean;
  variant?: "default" | "outline";
}

/** `시나리오 저장` 버튼. 눌러 이름 칸 팝오버를 열고, 저장에 성공하면 닫는다. */
export function SaveScenarioButton({
  onOpen,
  onSave,
  blocked,
  variant = "default",
}: SaveScenarioButtonProps) {
  const [open, setOpen] = useState(false);

  const handleOpenChange = (next: boolean) => {
    if (next && !onOpen()) return;
    setOpen(next);
  };

  const save = async (name: string | null) => {
    const saved = await onSave(name);
    if (saved) setOpen(false);
    return saved;
  };

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>
        <Button type="button" variant={variant}>
          시나리오 저장
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        className="w-[calc(100vw-2rem)] p-4 sm:w-80"
      >
        <SaveScenarioForm onSave={save} blocked={blocked} />
      </PopoverContent>
    </Popover>
  );
}
