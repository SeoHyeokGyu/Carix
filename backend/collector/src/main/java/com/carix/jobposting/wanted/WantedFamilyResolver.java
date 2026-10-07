package com.carix.jobposting.wanted;

import com.carix.jobposting.normalize.JobFamilyClassifier;
import java.util.List;
import java.util.Set;

/** 원티드 개발(518) 하위 직군을 대표 직무 하나로 고른다(설계 §5.3). 여럿이면 AI > DATA > DEVELOPER > OTHER. */
public class WantedFamilyResolver {

  private static final Set<Integer> AI = Set.of(1634);
  private static final Set<Integer> DATA = Set.of(655, 1024, 1025, 1022, 10231);
  private static final Set<Integer> OTHER = Set.of(876, 877, 1026, 672, 10536, 795, 793);

  private final JobFamilyClassifier titleRule = new JobFamilyClassifier();

  public String resolve(List<Integer> childTagIds, String title) {
    if (childTagIds.stream().anyMatch(AI::contains)
        || JobFamilyClassifier.AI.equals(titleRule.classifyItCategoryTitle(title))) {
      return JobFamilyClassifier.AI;
    }
    if (childTagIds.stream().anyMatch(DATA::contains)) {
      return JobFamilyClassifier.DATA;
    }
    if (childTagIds.isEmpty() || childTagIds.stream().anyMatch(id -> !OTHER.contains(id))) {
      return JobFamilyClassifier.DEVELOPER;
    }
    return JobFamilyClassifier.OTHER;
  }
}
