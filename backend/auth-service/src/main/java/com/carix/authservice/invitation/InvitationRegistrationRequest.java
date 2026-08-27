package com.carix.authservice.invitation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "베타 초대 등록 요청")
public record InvitationRegistrationRequest(
    @Schema(example = "[\"beta1@example.com\", \"beta2@example.com\"]")
        @NotEmpty
        @Size(max = 100)
        List<@NotBlank @Email String> emails) {}
