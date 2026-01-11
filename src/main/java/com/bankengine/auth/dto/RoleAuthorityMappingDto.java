package com.bankengine.auth.dto;

import com.bankengine.auth.validation.ValidAuthorities;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Data
public class RoleAuthorityMappingDto {

    @NotBlank(message = "Role name cannot be empty.")
    private String roleName;

    @NotEmpty(message = "Authorities set cannot be empty.")
    @ValidAuthorities
    private Set<String> authorities;

}