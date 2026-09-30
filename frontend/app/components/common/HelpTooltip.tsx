import { useState, useRef, useEffect, type ReactNode } from "react";
import { HelpCircle } from "lucide-react";
import { Button } from "@/components/ui/button";

/** 팝오버와 화면 가장자리 사이에 남길 여백(px). */
const VIEWPORT_MARGIN = 16;

/**
 * 열린 팝오버가 화면 밖으로 나간 만큼 안쪽으로 민다. 폭이 고정이고 아이콘 기준으로
 * 붙기 때문에, 아이콘이 화면 가운데 가까이 있으면 좁은 폰에서 한쪽이 잘린다.
 * 상태가 아니라 DOM 에 바로 적는다 — 측정은 그려진 뒤에만 가능하고, 상태로 두면
 * 한 번 더 렌더한다.
 */
function keepInViewport(tooltip: HTMLDivElement | null) {
  if (!tooltip) return;
  const { left, right } = tooltip.getBoundingClientRect();
  const overflowRight = right - (window.innerWidth - VIEWPORT_MARGIN);
  const overflowLeft = VIEWPORT_MARGIN - left;
  if (overflowRight > 0) {
    tooltip.style.translate = `${-overflowRight}px 0`;
  } else if (overflowLeft > 0) {
    tooltip.style.translate = `${overflowLeft}px 0`;
  }
}

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
        className="size-5 text-carix-text/40 hover:bg-transparent hover:text-carix-text focus-visible:text-carix-text dark:hover:bg-transparent"
      >
        <HelpCircle className="size-3.5" />
      </Button>

      {showTooltip && (
        <div
          ref={keepInViewport}
          role="tooltip"
          className={`absolute bottom-full mb-2 z-50 w-64 max-w-[calc(100vw-2rem)] p-3 rounded-lg border border-carix-border bg-carix-surface/95 backdrop-blur-sm text-xs text-carix-text shadow-md animate-in fade-in-0 zoom-in-95 ${alignmentClasses}`}
        >
          {children}
        </div>
      )}
    </div>
  );
}
