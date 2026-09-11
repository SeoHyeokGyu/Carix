package com.carix.authservice.user;

/** 온보딩 단계를 뒤로 옮기려는 시도. 지금은 정당한 역방향 전환이 없다 — 필요해지면 명시적으로 허용해야 한다(User#changeOnboardingStep). */
public class OnboardingStepRegressionException extends RuntimeException {

  public OnboardingStepRegressionException(String currentStep, String requestedStep) {
    super("온보딩 단계를 되돌릴 수 없습니다 (현재: " + currentStep + ", 요청: " + requestedStep + ")");
  }
}
