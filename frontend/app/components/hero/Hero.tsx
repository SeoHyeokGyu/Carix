import { HERO_INTRO } from "../../content/landing";
import { IndexCrossoverLine } from "./IndexCrossoverLine";

export function Hero() {
  return (
    <section aria-labelledby="hero-heading" className="px-6 py-24 md:py-32">
      <div className="mx-auto flex max-w-5xl flex-col items-center gap-10 text-center md:flex-row md:text-left">
        <div className="flex-1">
          <h1
            id="hero-heading"
            className="font-display text-4xl font-semibold tracking-tight text-carix-text md:text-5xl"
          >
            Carix
          </h1>
          {HERO_INTRO.paragraphs.map((paragraph) => (
            <p
              key={paragraph}
              className="mt-6 text-lg leading-relaxed text-carix-text/80"
            >
              {paragraph}
            </p>
          ))}
          <ul className="mt-8 flex flex-col gap-3 text-left">
            {HERO_INTRO.questions.map((question) => (
              <li
                key={question}
                className="rounded-lg border border-carix-border bg-carix-surface/80 px-4 py-3 text-carix-text"
              >
                {question}
              </li>
            ))}
          </ul>
        </div>
        <div className="flex flex-1 justify-center">
          <IndexCrossoverLine />
        </div>
      </div>
    </section>
  );
}
