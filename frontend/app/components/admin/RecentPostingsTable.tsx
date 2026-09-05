import type { RecentPostingResponse } from "../../lib/collectorApi";

interface RecentPostingsTableProps {
  postings: RecentPostingResponse[];
  isLoading: boolean;
}

export function RecentPostingsTable({
  postings,
  isLoading,
}: RecentPostingsTableProps) {
  const formatSalary = (min?: number, max?: number) => {
    if (!min && !max) return <span className="text-carix-text/40">비공개</span>;
    if (min && max)
      return `${min.toLocaleString()} ~ ${max.toLocaleString()}만원`;
    if (min) return `${min.toLocaleString()}만원 이상`;
    return `${max?.toLocaleString()}만원 이하`;
  };

  const formatExperience = (min?: number, max?: number) => {
    if (min === 0 && (!max || max === 0)) return "신입";
    if (min !== undefined && max !== undefined) {
      if (min === max) return `${min}년`;
      return `${min}~${max}년`;
    }
    if (min !== undefined) return `${min}년 이상`;
    if (max !== undefined) return `${max}년 이하`;
    return "경력무관";
  };

  return (
    <div className="rounded-xl border border-carix-border bg-carix-surface shadow-xs overflow-hidden">
      <div className="flex items-center justify-between border-b border-carix-border px-5 py-4">
        <div>
          <h3 className="text-sm font-bold text-carix-text">
            최근 수집된 공고 및 정규화 결과 미리보기
          </h3>
          <p className="text-xs text-carix-text/60 mt-0.5">
            가장 최근 DB에 적재된 20건의 채용공고와 스킬 매핑 현황
          </p>
        </div>
        <span className="text-xs text-carix-text/50">
          총 {postings.length}건 표시
        </span>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-left text-xs text-carix-text">
          <thead className="bg-carix-bg/60 border-b border-carix-border text-carix-text/60 uppercase font-semibold text-[11px]">
            <tr>
              <th className="px-5 py-3">원천</th>
              <th className="px-4 py-3">회사명</th>
              <th className="px-4 py-3">공고 제목</th>
              <th className="px-4 py-3">직군</th>
              <th className="px-4 py-3">경력</th>
              <th className="px-4 py-3">공개연봉</th>
              <th className="px-5 py-3">추출된 스킬 태그</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-carix-border">
            {isLoading && postings.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="px-5 py-8 text-center text-carix-text/50"
                >
                  공고 데이터를 불러오는 중입니다...
                </td>
              </tr>
            ) : postings.length === 0 ? (
              <tr>
                <td
                  colSpan={7}
                  className="px-5 py-8 text-center text-carix-text/50"
                >
                  아직 수집된 공고가 없습니다. 상단에서 크롤링 또는 시드 적재를
                  실행해 보세요.
                </td>
              </tr>
            ) : (
              postings.map((p) => (
                <tr
                  key={p.id}
                  className="hover:bg-carix-border/20 transition-colors"
                >
                  <td className="px-5 py-3 whitespace-nowrap">
                    <span
                      className={`inline-block rounded px-2 py-0.5 text-[11px] font-semibold ${
                        p.source === "JOBKOREA"
                          ? "bg-blue-500/10 text-blue-600 dark:text-blue-400"
                          : "bg-orange-500/10 text-orange-600 dark:text-orange-400"
                      }`}
                    >
                      {p.source === "JOBKOREA" ? "잡코리아" : "사람인"}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-medium text-carix-text whitespace-nowrap">
                    {p.companyName || "회사명 미기재"}
                  </td>
                  <td
                    className="px-4 py-3 max-w-xs truncate text-carix-text"
                    title={p.title}
                  >
                    {p.title}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span className="rounded bg-carix-border/50 px-2 py-0.5 text-[11px] font-mono">
                      {p.jobFamily || "UNKNOWN"}
                    </span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-carix-text/70">
                    {formatExperience(p.experienceMin, p.experienceMax)}
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-carix-text/70">
                    {formatSalary(p.salaryMin, p.salaryMax)}
                  </td>
                  <td className="px-5 py-3">
                    <div className="flex flex-wrap gap-1">
                      {p.skills && p.skills.length > 0 ? (
                        p.skills.map((skill) => (
                          <span
                            key={skill}
                            className="inline-block rounded-full bg-carix-info/10 text-carix-info px-2 py-0.5 text-[10px] font-medium"
                          >
                            #{skill}
                          </span>
                        ))
                      ) : (
                        <span className="text-carix-text/40 text-[11px]">
                          추출 스킬 없음
                        </span>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
