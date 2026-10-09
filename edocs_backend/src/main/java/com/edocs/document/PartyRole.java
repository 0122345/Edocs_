package com.edocs.document;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PartyRole {
    @JsonProperty("signer") SIGNER,
    @JsonProperty("approver") APPROVER,
    @JsonProperty("viewer") VIEWER
}
