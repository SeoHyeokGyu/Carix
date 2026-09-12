import { useEffect, useState } from "react";

const getInitialTheme = (): "light" | "dark" => {
  if (typeof window === "undefined") return "dark";
  const savedTheme = localStorage.getItem("theme") as "light" | "dark" | null;
  if (savedTheme) return savedTheme;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
};

export function ThemeToggle({
  inHeader = false,
  className,
}: {
  inHeader?: boolean;
  className?: string;
} = {}) {
  const [theme, setTheme] = useState<"light" | "dark">(getInitialTheme);

  useEffect(() => {
    if (theme === "dark") {
      document.documentElement.classList.add("dark");
      document.documentElement.classList.remove("light");
    } else {
      document.documentElement.classList.add("light");
      document.documentElement.classList.remove("dark");
    }
  }, [theme]);

  const toggleTheme = () => {
    const nextTheme = theme === "light" ? "dark" : "light";
    setTheme(nextTheme);
    localStorage.setItem("theme", nextTheme);
  };

  const defaultClass = inHeader
    ? "flex h-9 w-9 items-center justify-center rounded-lg border border-carix-border/60 bg-carix-surface hover:bg-carix-border/40 text-base cursor-pointer select-none transition-colors"
    : "fixed right-6 top-6 z-50 flex h-11 w-11 items-center justify-center rounded-full border border-carix-border bg-carix-surface/90 shadow-md backdrop-blur-md transition-all duration-300 hover:scale-105 active:scale-95 text-xl cursor-pointer select-none";

  return (
    <div
      onClick={toggleTheme}
      className={className || defaultClass}
      title="테마 전환"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          toggleTheme();
        }
      }}
    >
      {theme === "light" ? "🌙" : "☀️"}
    </div>
  );
}
