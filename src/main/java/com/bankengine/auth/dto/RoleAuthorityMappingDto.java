package com.bankengine.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class RoleAuthorityMappingDto {

    @NotBlank
    private String roleName;

    @NotEmpty
    private Set<String> authorities;

    // Optional: Include bankId if roles are specific to a client bank
    private String bankId;
}