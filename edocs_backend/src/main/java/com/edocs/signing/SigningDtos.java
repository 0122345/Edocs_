package com.edocs.signing;

import com.edocs.document.DocumentDtos.DocumentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class SigningDtos {

    private SigningDtos() {
    }

    public record OtpSent(String sentTo) {
    }

    // signature: drawn image as a data URL, or the typed name.
    public record SignRequest(
            @NotBlank @Size(max = 20) String mode,
            @NotBlank @Pattern(regexp = "\\d{6}", message = "must be the 6-digit code") String otp,
            @NotBlank @Size(max = 500_000) String signature) {
    }

    public record SignResult(String txId, DocumentDto document) {
    }
}
