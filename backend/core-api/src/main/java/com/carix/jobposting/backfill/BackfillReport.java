package com.carix.jobposting.backfill;

/** 백필 1회 실행 결과. dry-run 이면 같은 수치를 계산만 하고 쓰지 않는다. */
public record BackfillReport(
    boolean dryRun,
    int postingsScanned,
    int salaryFilled,
    int salaryCleared,
    int jobFamilyFilled,
    int skillLinksRewritten,
    int skillsRecategorized,
    int orphanSkillsRemoved,
    int rawIngestMissing
) {

  @Override
  public String toString() {
    return ("[backfill] dryRun=%s scanned=%d salary=%d salaryCleared=%d jobFamily=%d "
        + "skillLinks=%d recategorized=%d orphansRemoved=%d rawMissing=%d")
        .formatted(dryRun, postingsScanned, salaryFilled, salaryCleared, jobFamilyFilled,
            skillLinksRewritten, skillsRecategorized, orphanSkillsRemoved, rawIngestMissing);
  }
}
