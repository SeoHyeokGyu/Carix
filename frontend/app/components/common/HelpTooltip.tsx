import { useState, useRef, useEffect, type ReactNode } from "react";
import { HelpCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

interface HelpTooltipProps {
  label: string;
  children: ReactNode;
  align?: "left" | "right" | "center";
}

export function HelpTooltip({
  label,
  children,
  align = "left",
}: HelpTooltipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isPinned, setIsPinned] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  // 바깥 클릭 시 닫기
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setIsPinned(false);
        setIsOpen(false);
      }
    }
    if (isOpen || isPinned) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isOpen, isPinned]);

  // ESC 키 누를 때 닫기
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsPinned(false);
        setIsOpen(false);
      }
    }
    if (isOpen || isPinned) {
      document.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, isPinned]);

  const alignmentClasses =
    align === "right"
      ? "right-0"
      : align === "center"
        ? "left-1/2 -translate-x-1/2"
        : "left-0";

  const handleMouseEnter = () => {
    setIsOpen(true);
  };

  const handleMouseLeave = () => {
    if (!isPinned) {
      setIsOpen(false);
    }
  };

  const handleClick = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (isPinned) {
      setIsPinned(false);
      setIsOpen(false);
    } else {
      setIsPinned(true);
      setIsOpen(true);
    }
  };

  const showTooltip = isOpen || isPinned;

  return (
    <div
      ref={containerRef}
      className="relative inline-flex items-center"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <Button
        type="button"
        variant="ghost"
        size="icon-xs"
        aria-label={`${label} 도움말`}
        aria-expanded={showTooltip}
        onClick={handleClick}
        className="size-5 text-carix-text/40 hover:bg-transparent hover:text-carix-text"
      >
        <HelpCircle className="h-3.5 w-3.5" />
      </Button>

      {showTooltip && (
        <div
          role="tooltip"
          className={`absolute bottom-full mb-2 z-50 w-64 p-3 rounded-lg border border-carix-border bg-carix-surface/95 backdrop-blur-sm text-xs text-carix-text shadow-md animate-in fade-in-0 zoom-in-95 ${alignmentClasses}`}
        >
          {children}
        </div>
      )}
    </div>
  );
}
