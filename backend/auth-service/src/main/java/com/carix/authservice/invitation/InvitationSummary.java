package com.carix.authservice.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "초대 단건 요약")
public record InvitationSummary(
    String email, String status, OffsetDateTime expiresAt, OffsetDateTime createdAt) {

  static InvitationSummary from(Invitation invitation) {
    return new InvitationSummary(
        invitation.getEmail(),
        invitation.getStatus(),
        invitation.getExpiresAt(),
        invitation.getCreatedAt());
  }
}
