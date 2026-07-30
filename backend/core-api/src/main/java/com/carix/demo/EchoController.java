package com.carix.demo;

import com.carix.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Swagger UI와 배포 경로를 눈으로 확인하기 위한 임시 엔드포인트.
 *
 * <p>요청 스키마·검증·공통 에러 포맷이 실제로 문서에 드러나는지 보려는 것이 전부다.
 * 도메인 의미는 없다.
 *
 * <p>TODO: (cjkang) 실제 도메인 API가 들어오면 이 패키지째 삭제한다.
 */
@RestController
@RequestMapping("/v1/demo")
@Tag(name = "demo", description = "임시 확인용. 도메인 API 도입 시 제거한다")
public class EchoController {

	@Operation(
			summary = "입력을 그대로 돌려준다",
			description = "Swagger UI의 Try it out으로 요청 본문·검증·에러 포맷을 확인하는 용도다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "정상"),
			@ApiResponse(
					responseCode = "400",
					description = "검증 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))})
	@PostMapping("/echo")
	public EchoResponse echo(@Valid @RequestBody EchoRequest request) {
		return new EchoResponse(request.message(), Instant.now());
	}

	@Schema(description = "에코 요청")
	public record EchoRequest(
			@NotBlank
			@Size(max = 200)
			@Schema(description = "돌려받을 문자열", example = "안녕 Carix")
			String message) {
	}

	@Schema(description = "에코 응답")
	public record EchoResponse(String message, Instant receivedAt) {
	}
}
