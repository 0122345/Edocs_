package com.edocs.document;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DocStatus {
    @JsonProperty("draft") DRAFT,
    @JsonProperty("in_review") IN_REVIEW,
    @JsonProperty("out_for_signature") OUT_FOR_SIGNATURE,
    @JsonProperty("signed") SIGNED,
    @JsonProperty("archived") ARCHIVED;

    public boolean isSealed() {
        return this == SIGNED || this == ARCHIVED;
    }
}
