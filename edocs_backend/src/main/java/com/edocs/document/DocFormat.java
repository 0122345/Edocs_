package com.edocs.document;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DocFormat {
    @JsonProperty("pdf") PDF,
    @JsonProperty("docx") DOCX
}
