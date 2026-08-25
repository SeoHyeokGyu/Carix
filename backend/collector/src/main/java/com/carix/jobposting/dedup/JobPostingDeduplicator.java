package com.carix.jobposting.dedup;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채용공고 원천 간 중복 제거(Deduplication) 엔진.
 *
 * <p>잡코리아, 사람인 등 다수 원천에 등록된 동일 회사·유사 제목의 공고를 매칭하여 단일 {@code dedup_group_id} 를 부여합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingDeduplicator {

  public static final String DEDUP_RULE_VERSION = "v1";
  private static final double TITLE_SIMILARITY_THRESHOLD = 0.4;

  private final CompanyNormalizer companyNormalizer;
  private final TitleNormalizer titleNormalizer;
  private final JobPostingRepository jobPostingRepository;

  /**
   * DB에 저장된 전체 채용공고를 대상으로 원천 간 중복제거를 수행하고 dedup_group_id 를 갱신합니다.
   *
   * @return 중복 매칭으로 묶인 그룹 수
   */
  @Transactional
  public int deduplicateAll() {
    log.info("[공고중복제거] 전체 채용공고 원천 간 중복제거를 시작합니다 (규칙 버전: {})...", DEDUP_RULE_VERSION);
    List<JobPosting> allPostings = jobPostingRepository.findAll();

    int matchedGroups = deduplicate(allPostings);
    jobPostingRepository.saveAll(allPostings);

    log.info("[공고중복제거] 중복제거 완료. 총 {}건 처리됨, {}개의 다중 공고 그룹 형성", allPostings.size(), matchedGroups);
    return matchedGroups;
  }

  /**
   * 주어진 공고 목록 내에서 회사명 및 제목 유사도 휴리스틱을 적용해 dedup_group_id 를 할당합니다.
   *
   * @param postings 대상 공고 목록
   * @return 2건 이상 묶인 중복 그룹의 개수
   */
  public int deduplicate(List<JobPosting> postings) {
    if (postings == null || postings.isEmpty()) {
      return 0;
    }

    // 1. 회사명 정규화 키별로 그룹핑
    Map<String, List<PostingTokenWrapper>> byCompany = new HashMap<>();
    for (JobPosting p : postings) {
      String normCompany = companyNormalizer.normalize(p.getCompanyName());
      if (normCompany.isEmpty()) {
        normCompany = "UNKNOWN_COMPANY_" + p.getId();
      }
      Set<String> tokens = titleNormalizer.extractKeywords(p.getTitle());
      byCompany
          .computeIfAbsent(normCompany, k -> new ArrayList<>())
          .add(new PostingTokenWrapper(p, tokens));
    }

    int multiItemGroups = 0;

    // 2. 회사별 내부에서 제목 유사도 및 기간 클러스터링
    for (List<PostingTokenWrapper> companyPostings : byCompany.values()) {
      if (companyPostings.size() == 1) {
        JobPosting single = companyPostings.get(0).posting();
        single.assignDedupGroup(UUID.randomUUID(), DEDUP_RULE_VERSION);
        continue;
      }

      // 유사도 기반 클러스터링 (Disjoint Set / Union-Find)
      int n = companyPostings.size();
      UnionFind uf = new UnionFind(n);

      for (int i = 0; i < n; i++) {
        for (int j = i + 1; j < n; j++) {
          PostingTokenWrapper w1 = companyPostings.get(i);
          PostingTokenWrapper w2 = companyPostings.get(j);

          if (isDuplicate(w1, w2)) {
            uf.union(i, j);
          }
        }
      }

      // 클러스터 루트별 UUID 부여
      Map<Integer, UUID> groupUuids = new HashMap<>();
      Map<Integer, Integer> groupSizes = new HashMap<>();

      for (int i = 0; i < n; i++) {
        int root = uf.find(i);
        groupUuids.putIfAbsent(root, UUID.randomUUID());
        groupSizes.put(root, groupSizes.getOrDefault(root, 0) + 1);
      }

      for (int i = 0; i < n; i++) {
        int root = uf.find(i);
        UUID groupId = groupUuids.get(root);
        companyPostings.get(i).posting().assignDedupGroup(groupId, DEDUP_RULE_VERSION);
      }

      for (int size : groupSizes.values()) {
        if (size > 1) {
          multiItemGroups++;
        }
      }
    }

    return multiItemGroups;
  }

  private boolean isDuplicate(PostingTokenWrapper w1, PostingTokenWrapper w2) {
    JobPosting p1 = w1.posting();
    JobPosting p2 = w2.posting();

    // 원천이 다르고 (예: 잡코리아 vs 사람인) 같은 회사이거나, 같은 원천 내 재등록 공고
    // 제목 키워드 자카드 유사도 비교
    double similarity = titleNormalizer.calculateJaccardSimilarity(w1.tokens(), w2.tokens());
    if (similarity >= TITLE_SIMILARITY_THRESHOLD) {
      return true;
    }

    // 한쪽 제목이 다른 쪽 제목의 키워드를 완전 포함하는 경우 (부분 집합)
    if (!w1.tokens().isEmpty() && !w2.tokens().isEmpty()) {
      if (w1.tokens().containsAll(w2.tokens()) || w2.tokens().containsAll(w1.tokens())) {
        return true;
      }
    }

    return false;
  }

  private record PostingTokenWrapper(JobPosting posting, Set<String> tokens) {}

  private static class UnionFind {
    private final int[] parent;

    public UnionFind(int size) {
      parent = new int[size];
      for (int i = 0; i < size; i++) {
        parent[i] = i;
      }
    }

    public int find(int i) {
      if (parent[i] == i) {
        return i;
      }
      return parent[i] = find(parent[i]);
    }

    public void union(int i, int j) {
      int rootI = find(i);
      int rootJ = find(j);
      if (rootI != rootJ) {
        parent[rootI] = rootJ;
      }
    }
  }
}
