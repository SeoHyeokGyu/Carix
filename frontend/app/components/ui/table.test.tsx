import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "./table";

function Sample({ variant }: { variant?: "framed" | "plain" }) {
  return (
    <Table variant={variant}>
      <TableHeader>
        <TableRow>
          <TableHead>기준월</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        <TableRow>
          <TableCell>2026-09</TableCell>
        </TableRow>
      </TableBody>
    </Table>
  );
}

describe("Table", () => {
  // c13 — 열이 많은 표가 좁은 폭에서 한글을 글자 단위로 쪼개지 않고 가로 스크롤된다.
  it("표를 가로 스크롤 컨테이너로 감싼다", () => {
    render(<Sample />);
    const scroll = screen.getByRole("table").parentElement;
    expect(scroll).toHaveAttribute("data-slot", "table-scroll");
    expect(scroll).toHaveClass("overflow-x-auto");
  });

  it("기본값은 테두리·배경이 있는 틀이다", () => {
    const { container } = render(<Sample />);
    const frame = container.querySelector('[data-slot="table-container"]');
    expect(frame).toHaveClass("rounded-xl", "border", "bg-carix-surface");
  });

  // 이미 Card 안에 놓인 표가 박스 안의 박스가 되지 않는다.
  it("plain 은 틀을 벗는다", () => {
    const { container } = render(<Sample variant="plain" />);
    const frame = container.querySelector('[data-slot="table-container"]');
    expect(frame).not.toHaveClass("border");
    expect(frame).not.toHaveClass("bg-carix-surface");
    // 스크롤은 plain 에서도 유지한다.
    expect(screen.getByRole("table").parentElement).toHaveClass(
      "overflow-x-auto",
    );
  });
});
