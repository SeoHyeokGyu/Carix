package com.carix.authservice.invitation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 베타 초대 등록. 관리자 화면은 MVP 범위 밖이라 운영자가 직접 호출하는 내부 경로로 둔다.
 *
 * <p>경로가 {@code /internal/**} 인 이유는 게이트웨이가 {@code /api/v1/auth/**} 와 {@code /api/v1/**} 만 백엔드로
 * 라우팅하기 때문이다. 그 밖의 경로는 프런트엔드로 가므로 이 엔드포인트는 외부에서 도달하지 않는다. 그래도 클러스터 안에서는 열려 있으므로 {@code
 * InternalApiKeyFilter} 가 공유 시크릿을 검사한다.
 */
@RestController
@RequestMapping("/internal/invitations")
@Tag(name = "internal", description = "운영자 전용 내부 경로 (게이트웨이 미라우팅)")
public class InvitationController {

  private final InvitationService invitationService;

  public InvitationController(InvitationService invitationService) {
    this.invitationService = invitationService;
  }

  @Operation(summary = "초대 등록", description = "이미 초대된 이메일은 건너뛴다. 같은 목록을 다시 보내도 결과가 같다.")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public InvitationRegistrationResponse register(
      @Valid @RequestBody InvitationRegistrationRequest request) {
    return invitationService.register(request.emails());
  }

  @Operation(summary = "초대 목록 조회", description = "등록 결과 확인용. 베타 규모(10~30명)를 전제로 페이지네이션을 두지 않는다.")
  @GetMapping
  public List<InvitationSummary> list() {
    return invitationService.findAll();
  }
}
