package com.edocs.identity;

import com.fasterxml.jackson.annotation.JsonProperty;

// eIDAS signature assurance levels: simple, advanced, qualified.
public enum SignatureLevel {
    @JsonProperty("ses") SES,
    @JsonProperty("aes") AES,
    @JsonProperty("qes") QES
}
