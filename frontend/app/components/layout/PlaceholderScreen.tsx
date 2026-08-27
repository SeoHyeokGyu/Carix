interface PlaceholderScreenProps {
  screenId: string;
  title: string;
  description: string;
}

/** 라우트 골격만 잡힌 화면. 실제 UI 가 들어오면 이 컴포넌트를 걷어낸다. */
export function PlaceholderScreen({
  screenId,
  title,
  description,
}: PlaceholderScreenProps) {
  return (
    <section
      aria-labelledby={`${screenId}-heading`}
      className="mx-auto w-full max-w-3xl px-6 py-16"
    >
      <p className="font-display text-sm font-semibold tracking-widest text-carix-info">
        {screenId}
      </p>
      <h1
        id={`${screenId}-heading`}
        className="mt-3 font-display text-3xl font-bold text-carix-text sm:text-4xl"
      >
        {title}
      </h1>
      <p className="mt-4 text-base leading-relaxed text-carix-text/70">
        {description}
      </p>
      <p className="mt-10 rounded-lg border border-dashed border-carix-border bg-carix-surface px-5 py-4 text-sm text-carix-text/60">
        라우트 골격만 있는 화면입니다. UI 는 아직 구현되지 않았습니다.
      </p>
    </section>
  );
}
