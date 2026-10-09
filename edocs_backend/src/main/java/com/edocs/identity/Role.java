package com.edocs.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Role {
    @JsonProperty("admin") ADMIN,
    @JsonProperty("legal") LEGAL,
    @JsonProperty("signer") SIGNER,
    @JsonProperty("auditor") AUDITOR
}
