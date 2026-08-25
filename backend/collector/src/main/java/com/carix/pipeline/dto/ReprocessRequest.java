package com.carix.pipeline.dto;

public record ReprocessRequest(Boolean dryRun) {
  public ReprocessRequest() {
    this(true);
  }
}
