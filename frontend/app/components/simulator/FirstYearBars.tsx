import type { EvaluationResponse } from "../../lib/api/scenario";
import { firstYearItems } from "../../lib/simulatorCompare";
import { amountLabel } from "./chartScale";

type FirstYear = NonNullable<EvaluationResponse["move"]>["firstYear"];

const CAPTION = "지금 회사 첫해를 0으로 놓고 항목별로 더하고 뺀 값입니다.";

/** 0 기준 왼쪽(음수)·오른쪽(양수)으로 뻗는 가로 막대. 막대 폭은 절댓값 최대 대비 %. */
export function FirstYearBars({ firstYear }: { firstYear: FirstYear }) {
  const items = firstYearItems(firstYear);
  const maxAbs = Math.max(...items.map((item) => Math.abs(item.value)), 1);
  const percent = (value: number) => `${(Math.abs(value) / maxAbs) * 100}%`;

  return (
    <figure className="m-0">
      <ul
        aria-hidden="true"
        className="m-0 flex list-none flex-col gap-2 p-0 text-sm"
      >
        {items.map((item) => (
          <li
            key={item.key}
            className={
              item.isTotal
                ? "mt-1 grid grid-cols-[5.5rem_1fr_1fr_4rem] items-center gap-x-2 border-t border-carix-border pt-3 font-bold"
                : "grid grid-cols-[5.5rem_1fr_1fr_4rem] items-center gap-x-2"
            }
          >
            <span className="leading-tight">{item.label}</span>
            <span className="flex h-6 justify-end">
              {item.value < 0 && (
                <span
                  style={{ width: percent(item.value) }}
                  className={`rounded-l-sm bg-carix-danger/45 ${
                    item.isTotal ? "border-2 border-carix-danger" : ""
                  }`}
                />
              )}
            </span>
            <span className="flex h-6 justify-start border-l-2 border-carix-text/70">
              {item.value > 0 && (
                <span
                  style={{ width: percent(item.value) }}
                  className={`rounded-r-sm bg-carix-positive/45 ${
                    item.isTotal ? "border-2 border-carix-positive" : ""
                  }`}
                />
              )}
            </span>
            <span className="text-right tabular-nums">
              {amountLabel(item.value)}
            </span>
          </li>
        ))}
      </ul>
      <ul className="sr-only">
        {items.map((item) => (
          <li key={item.key}>
            {item.label} {amountLabel(item.value)}원
          </li>
        ))}
      </ul>
      <figcaption className="mt-3 text-sm text-carix-text/70">
        {CAPTION}
      </figcaption>
    </figure>
  );
}
