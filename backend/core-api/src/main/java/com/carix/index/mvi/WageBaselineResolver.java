package com.carix.index.mvi;

import com.carix.index.dto.WageBaseline;
import com.carix.profile.domain.CareerProfile;
import com.carix.wagestat.WageStatObservation;
import com.carix.wagestat.WageStatObservationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 공식 임금통계 기준선(WageBaseline) 해석기.
 *
 * <p>사용자의 커리어 프로필(직무, 경력 브래킷, 지역)에 해당하는 공식 임금통계 중앙값을 조회하여 MVI 산출 결과에 기준선 정보를 첨부합니다.
 */
@Component
@RequiredArgsConstructor
public class WageBaselineResolver {

  private final WageStatObservationRepository wageStatObservationRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  /**
   * 사용자 커리어 프로필을 바탕으로 공식 임금통계 기준선을 조회합니다.
   *
   * @param profile 사용자 커리어 프로필
   * @return 임금통계 기준선 (조회 실패 시 null)
   */
  public WageBaseline resolveBaseline(CareerProfile profile) {
    if (profile == null) {
      return null;
    }

    String jobFamily = profile.getJobFamily() != null ? profile.getJobFamily() : "DEVELOPER";
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = peerGroupFallbackResolver.mapExperienceBracket(years);
    String broadRegion = peerGroupFallbackResolver.extractBroadRegion(profile.getRegion());

    Optional<WageStatObservation> obsOpt =
        wageStatObservationRepository.findLatestBaseline(jobFamily, bracket, broadRegion);

    if (obsOpt.isEmpty()) {
      // 광역 지역으로 없을 경우 전국 기준 또는 기본 검색 시도
      obsOpt = wageStatObservationRepository.findLatestBaseline(jobFamily, bracket, "전국");
    }

    return obsOpt
        .map(
            obs ->
                new WageBaseline(
                    obs.getWageMedian(), obs.getWageDefinition(), obs.getStatVintage()))
        .orElse(null);
  }
}
