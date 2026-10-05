package com.carix.pipeline.health;

import java.util.Optional;

/** core-api 엔진 규칙 버전. 못 받으면 비운다. */
public interface EngineVersionClient {
  Optional<String> ruleVersion();
}
