package com.carix.collector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReprocessRequest(@JsonProperty("dryRun") Boolean dryRun) {
  public ReprocessRequest {
    if (dryRun == null) {
      dryRun = true;
    }
  }
}
