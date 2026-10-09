package com.edocs.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum KycStatus {
    @JsonProperty("verified") VERIFIED,
    @JsonProperty("pending") PENDING,
    @JsonProperty("none") NONE
}
