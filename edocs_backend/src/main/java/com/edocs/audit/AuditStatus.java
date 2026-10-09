package com.edocs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AuditStatus {
    @JsonProperty("verified") VERIFIED,
    @JsonProperty("pending") PENDING,
    @JsonProperty("failed") FAILED
}
