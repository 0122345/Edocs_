package com.edocs.identity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.identity.IdentityDtos.SettingsDto;
import com.edocs.security.CurrentUser;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Settings")
@RestController
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public SettingsDto get() {
        return settings.get(CurrentUser.get().orgId());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('settings:manage')")
    public SettingsDto update(@Valid @RequestBody SettingsDto body) {
        return settings.update(CurrentUser.get().orgId(), body);
    }
}
