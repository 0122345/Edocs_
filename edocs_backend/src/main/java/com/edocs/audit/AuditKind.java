package com.edocs.audit;

import com.fasterxml.jackson.annotation.JsonProperty;

// ActionType from the class diagram.
public enum AuditKind {
    @JsonProperty("signature") SIGNATURE,
    @JsonProperty("redline") REDLINE,
    @JsonProperty("view") VIEW,
    @JsonProperty("key-rotation") KEY_ROTATION,
    @JsonProperty("anchor") ANCHOR,
    @JsonProperty("legal-hold") LEGAL_HOLD,
    @JsonProperty("create") CREATE,
    @JsonProperty("share") SHARE
}
