package com.carix.authservice.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "베타 초대 등록 결과")
public record InvitationRegistrationResponse(
    @Schema(description = "새로 등록된 이메일") List<String> created,
    @Schema(description = "이미 초대돼 있어 건너뛴 이메일") List<String> skipped) {}
