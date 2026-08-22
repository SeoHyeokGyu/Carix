package com.carix.jobposting.normalize;

import java.util.Map;
import java.util.Set;

/**
 * 구 단위 지역을 시도로 올린다. 원천은 `서울 강남구` 처럼 시도와 시군구를 공백으로 이어 주고, 뒤에 `외` 같은 꼬리표가 붙기도 한다.
 *
 * <p>첫 어절이 곧 시도인 것은 아니다. 실측 800건에 `부산전체`, `세종특별자치시`, 전남과 광주를 한 라벨로 묶은 `전남광주`, 지역이 아닌 `정규직`, 국내 시도가
 * 아닌 `일본` 이 섞여 있다. 시도 화이트리스트를 통과한 값만 집계 키로 쓰고 나머지는 UNKNOWN 으로 보낸다 — 잘못된 키는 완화 Level 3(지역 확장)을 조용히
 * 망가뜨린다.
 */
public final class RegionRollup {

  public static final String UNKNOWN = "UNKNOWN";

  private static final Set<String> SIDO =
      Set.of(
          "서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북",
          "경남", "제주");

  /** 원천이 정식 명칭을 그대로 주는 경우가 있다(실측: `세종특별자치시`). */
  private static final Map<String, String> FULL_NAMES =
      Map.ofEntries(
          Map.entry("서울특별시", "서울"),
          Map.entry("부산광역시", "부산"),
          Map.entry("대구광역시", "대구"),
          Map.entry("인천광역시", "인천"),
          Map.entry("광주광역시", "광주"),
          Map.entry("대전광역시", "대전"),
          Map.entry("울산광역시", "울산"),
          Map.entry("세종특별자치시", "세종"),
          Map.entry("경기도", "경기"),
          Map.entry("강원특별자치도", "강원"),
          Map.entry("충청북도", "충북"),
          Map.entry("충청남도", "충남"),
          Map.entry("전북특별자치도", "전북"),
          Map.entry("전라남도", "전남"),
          Map.entry("경상북도", "경북"),
          Map.entry("경상남도", "경남"),
          Map.entry("제주특별자치도", "제주"));

  /** 사람인·잡코리아가 전남과 광주를 묶어 쓰는 라벨이다. 시군구로 갈라야 한다. */
  private static final String JEONNAM_GWANGJU = "전남광주";

  private static final String ALL_SUFFIX = "전체";

  private RegionRollup() {}

  public static String toSido(String region) {
    if (region == null || region.isBlank()) {
      return UNKNOWN;
    }
    String[] tokens = region.strip().split("\\s+");
    if (JEONNAM_GWANGJU.equals(tokens[0])) {
      return splitJeonnamGwangju(tokens);
    }
    return canonical(tokens[0]);
  }

  private static String canonical(String head) {
    String name =
        head.length() > ALL_SUFFIX.length() && head.endsWith(ALL_SUFFIX)
            ? head.substring(0, head.length() - ALL_SUFFIX.length())
            : head;
    String sido = FULL_NAMES.getOrDefault(name, name);
    return SIDO.contains(sido) ? sido : UNKNOWN;
  }

  /**
   * 전남에는 자치구가 없다 — `전남광주` 뒤의 구는 전부 광주광역시고, 시·군은 전남이다. 시군구가 없는 `전남광주` 단독 표기는 어느 쪽인지 판정할 근거가 없어
   * UNKNOWN 이다.
   */
  private static String splitJeonnamGwangju(String[] tokens) {
    if (tokens.length < 2) {
      return UNKNOWN;
    }
    return tokens[1].endsWith("구") ? "광주" : "전남";
  }
}
