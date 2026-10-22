import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ThemeToggle } from "./ThemeToggle";

describe("ThemeToggle", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove("light", "dark");
  });

  it("저장된 테마가 없고 시스템이 다크 모드를 선호하면 다크 테마로 초기화한다", () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === "(prefers-color-scheme: dark)",
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(<ThemeToggle />);
    expect(screen.getByTitle("테마 전환")).toHaveTextContent("☀️");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(document.documentElement.classList.contains("light")).toBe(false);
  });

  it("localStorage에 저장된 테마가 있으면 해당 테마를 우선 적용한다", () => {
    localStorage.setItem("theme", "light");

    render(<ThemeToggle />);
    expect(screen.getByTitle("테마 전환")).toHaveTextContent("🌙");
    expect(document.documentElement.classList.contains("light")).toBe(true);
  });

  it("클릭 시 테마를 토글하고 localStorage에 저장한다", () => {
    localStorage.setItem("theme", "dark");

    render(<ThemeToggle />);
    const toggle = screen.getByTitle("테마 전환");
    expect(toggle).toHaveTextContent("☀️");

    // Toggle from dark to light
    fireEvent.click(toggle);
    expect(toggle).toHaveTextContent("🌙");
    expect(localStorage.getItem("theme")).toBe("light");
    expect(document.documentElement.classList.contains("light")).toBe(true);
    expect(document.documentElement.classList.contains("dark")).toBe(false);

    // Toggle from light to dark
    fireEvent.click(toggle);
    expect(toggle).toHaveTextContent("☀️");
    expect(localStorage.getItem("theme")).toBe("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(document.documentElement.classList.contains("light")).toBe(false);
  });
});
