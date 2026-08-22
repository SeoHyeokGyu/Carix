package com.carix.authservice.magiclink;

import java.util.UUID;

public record VerifyResponse(UUID userId, String email, boolean isOnboardingComplete) {}
