package com.edocs.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum NotificationType {
    @JsonProperty("signature_request") SIGNATURE_REQUEST,
    @JsonProperty("otp") OTP,
    @JsonProperty("invite") INVITE,
    @JsonProperty("share") SHARE,
    @JsonProperty("signed") SIGNED,
    @JsonProperty("system") SYSTEM
}
