package com.edocs.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Channel {
    @JsonProperty("email") EMAIL,
    @JsonProperty("sms") SMS,
    @JsonProperty("in_app") IN_APP
}
