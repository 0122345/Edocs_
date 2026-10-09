package com.edocs.signing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.signing.SigningDtos.OtpSent;
import com.edocs.signing.SigningDtos.SignRequest;
import com.edocs.signing.SigningDtos.SignResult;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Signing")
@RestController
@RequestMapping("/documents/{id}")
public class SigningController {

    private final SigningService signing;

    public SigningController(SigningService signing) {
        this.signing = signing;
    }

    @PostMapping("/otp")
    @PreAuthorize("hasAuthority('document:sign')")
    public OtpSent sendOtp(@PathVariable String id) {
        return signing.sendOtp(id);
    }

    @PostMapping("/signatures")
    @PreAuthorize("hasAuthority('document:sign')")
    public SignResult sign(@PathVariable String id, @Valid @RequestBody SignRequest req) {
        return signing.sign(id, req.mode(), req.otp(), req.signature());
    }
}
