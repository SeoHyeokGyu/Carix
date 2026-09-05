import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeToggle } from "./ThemeToggle";

describe("ThemeToggle", () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove("light", "dark");
  });

  it("initializes with dark theme if system prefers dark and no saved theme", () => {
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

  it("initializes with light theme if system prefers light and no saved theme", () => {
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    render(<ThemeToggle />);
    expect(screen.getByTitle("테마 전환")).toHaveTextContent("🌙");
    expect(document.documentElement.classList.contains("light")).toBe(true);
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });

  it("uses saved theme from localStorage when present", () => {
    localStorage.setItem("theme", "light");

    render(<ThemeToggle />);
    expect(screen.getByTitle("테마 전환")).toHaveTextContent("🌙");
    expect(document.documentElement.classList.contains("light")).toBe(true);
  });

  it("toggles theme on click and persists in localStorage", () => {
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

  it("toggles theme on Enter and Space keydown, but ignores other keys", () => {
    localStorage.setItem("theme", "dark");

    render(<ThemeToggle />);
    const toggle = screen.getByTitle("테마 전환");

    // Other key (e.g. Escape) should not toggle
    fireEvent.keyDown(toggle, { key: "Escape" });
    expect(toggle).toHaveTextContent("☀️");

    // Enter key toggles
    fireEvent.keyDown(toggle, { key: "Enter" });
    expect(toggle).toHaveTextContent("🌙");
    expect(localStorage.getItem("theme")).toBe("light");

    // Space key toggles back
    fireEvent.keyDown(toggle, { key: " " });
    expect(toggle).toHaveTextContent("☀️");
    expect(localStorage.getItem("theme")).toBe("dark");
  });
});
