package com.carix.authservice.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  private String email;

  /** BCrypt 해시. 매직링크 코드가 남아 있는 동안만 {@code null} 일 수 있다. */
  private String passwordHash;

  private String onboardingStep;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  protected User() {}

  public User(
      UUID id,
      String email,
      String passwordHash,
      String onboardingStep,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.onboardingStep = onboardingStep;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getOnboardingStep() {
    return onboardingStep;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  /**
   * 온보딩 진행 순서. {@code OnboardingUpdateRequest} 의 {@code @Pattern} 과 같은 여섯 값이다 — 그쪽은 "허용된 값인가"만 보고
   * 이쪽은 "그 값이 지금보다 앞인가"를 본다.
   */
  private static final List<String> STEP_ORDER =
      List.of("PROFILE", "INCOME", "EXPENSE", "SKILLS", "REVIEW", "COMPLETED");

  /**
   * 온보딩 단계를 옮긴다. 허용값(여섯 개 중 하나인가) 검증은 요청 경계(OnboardingUpdateRequest)에서 한다 — 여기서는 방향만 본다.
   *
   * <p>뒤로는 못 간다. 지금은 정당한 역방향 전환이 없다 — 검토(REVIEW) 화면의 "수정" 링크로 돌아가 앞선 화면을 다시 저장해도, 그 화면의 "다음"은 클라이언트
   * 가드(routeGuards.ts isForwardStep)가 앞으로 갈 때만 이 엔드포인트를 부르도록 이미 막는다 — 이 검증은 그 가드가 실수로 빠지거나 클라이언트
   * 버그가 있을 때의 방어선이다(2차 방어).
   *
   * <p>같은 단계로의 재전환(예: 이미 COMPLETED 인 사용자가 검토 화면을 다시 제출)은 뒤로 가는 것이 아니므로 허용한다 — 막으면 재진입이 허용된
   * 화면(guardOnboarding)에서 멀쩡한 재제출이 400 을 받는다.
   *
   * <p>현재 값이 STEP_ORDER 에 없는(손상된) 행이면 {@code indexOf} 가 -1 을 돌려줘 어떤 전환이든 "뒤가 아님"으로 통과시킨다 — 의도적이다.
   * 손상된 값을 원래 여섯 값 중 하나로 되돌릴 방법을 막아버리면 그 행은 영영 고칠 수 없다.
   */
  public void changeOnboardingStep(String step, OffsetDateTime now) {
    if (STEP_ORDER.indexOf(step) < STEP_ORDER.indexOf(this.onboardingStep)) {
      throw new OnboardingStepRegressionException(this.onboardingStep, step);
    }
    this.onboardingStep = step;
    this.updatedAt = now;
  }

  /** 온보딩을 처음부터 다시 시작하도록 1단계(PROFILE)로 초기화한다. 사용자의 명시적인 재시작 요청에 의해서만 호출된다. */
  public void resetOnboarding(OffsetDateTime now) {
    this.onboardingStep = "PROFILE";
    this.updatedAt = now;
  }

  public boolean isOnboardingComplete() {
    return "COMPLETED".equals(onboardingStep);
  }
}
