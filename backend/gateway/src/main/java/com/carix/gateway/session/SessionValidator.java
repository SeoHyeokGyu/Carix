package com.carix.gateway.session;

import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SessionValidator {

  Mono<UUID> validate(String sessionToken);
}
