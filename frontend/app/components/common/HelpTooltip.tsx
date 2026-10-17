import { HelpCircle } from "lucide-react";
import { type ReactNode, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { TextLink } from "@/components/ui/text-link";

/** 팝오버와 화면 가장자리 사이에 남길 여백(px). */
const VIEWPORT_MARGIN = 16;

/**
 * 마우스가 벗어난 뒤 닫기까지 기다리는 시간. 아이콘에서 팝오버 안 링크로 대각선으로 가면
 * 아이콘 옆 빈칸을 잠깐 지나는데, 그 순간 바로 닫으면 링크에 닿지 못한다.
 */
const HOVER_CLOSE_DELAY_MS = 150;

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
  /** 도움말 화면의 해당 섹션 주소. 주면 팝오버 아래에 「도움말에서 자세히 보기」를 단다. */
  moreHref?: string;
}

export function HelpTooltip({
  label,
  children,
  align = "left",
  moreHref,
}: HelpTooltipProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isPinned, setIsPinned] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cancelClose = () => {
    if (closeTimerRef.current) clearTimeout(closeTimerRef.current);
    closeTimerRef.current = null;
  };

  useEffect(() => cancelClose, []);

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
    cancelClose();
    setIsOpen(true);
  };

  const handleMouseLeave = () => {
    if (isPinned) return;
    cancelClose();
    closeTimerRef.current = setTimeout(
      () => setIsOpen(false),
      HOVER_CLOSE_DELAY_MS,
    );
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
        // 아이콘과 팝오버 사이 틈을 바깥 껍데기의 pb 로 채운다 — margin 이면 마우스가 틈을
        // 지나는 순간 컨테이너를 벗어나 닫혀, 호버로는 안의 링크에 닿지 못한다.
        <div
          ref={keepInViewport}
          role="tooltip"
          className={`absolute bottom-full pb-2 z-50 w-64 max-w-[calc(100vw-2rem)] ${alignmentClasses}`}
        >
          <div className="p-3 rounded-lg border border-carix-border bg-carix-surface/95 backdrop-blur-sm text-xs text-carix-text shadow-md animate-in fade-in-0 zoom-in-95">
            {children}
            {moreHref && (
              <div className="mt-2 border-t border-carix-border pt-2">
                <TextLink to={moreHref}>도움말에서 자세히 보기</TextLink>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
