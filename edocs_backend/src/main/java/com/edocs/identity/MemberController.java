package com.edocs.identity;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.identity.IdentityDtos.InviteMemberRequest;
import com.edocs.identity.IdentityDtos.UpdateMemberRequest;
import com.edocs.identity.IdentityDtos.UserDto;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Members")
@RestController
@RequestMapping("/members")
public class MemberController {

    private final MemberService members;

    public MemberController(MemberService members) {
        this.members = members;
    }

    @GetMapping
    public List<UserDto> list() {
        return members.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('members:manage')")
    public UserDto invite(@Valid @RequestBody InviteMemberRequest req) {
        return members.invite(req.email(), req.name(), req.role());
    }

    // Members may toggle their own MFA; everything else needs members:manage (checked in the service).
    @PatchMapping("/{id}")
    public UserDto update(@PathVariable String id, @RequestBody UpdateMemberRequest req) {
        return members.update(id, req);
    }
}
