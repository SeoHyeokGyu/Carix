package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "시뮬레이션 결과 응답")
public record SimulationResponse(
        @Schema(description = "시나리오 고유 ID") UUID id,
        @Schema(description = "시나리오 이름") String scenarioName,
        @Schema(description = "12개월 추정 잔류 보상액", example = "62100000") long stayCompensation12m,
        @Schema(description = "12개월 추정 이직 보상액", example = "60960000") long moveCompensation12m,
        @Schema(description = "실업급여 추정액", example = "3960000") long estimatedUnemploymentBenefit,
        @Schema(description = "잔류와 이직 간 차액", example = "-1140000") long difference,
        @Schema(description = "결과 유형 코드 (ESTIMATED_GAIN / ESTIMATED_OPPORTUNITY_COST)", example = "ESTIMATED_OPPORTUNITY_COST") String type,
        @Schema(description = "결과 유형 라벨명", example = "추정 기회비용") String typeLabel,
        @Schema(description = "주의 사항 텍스트", example = "이 계산에는 세금, 성과급...") String warningNote
) {}
