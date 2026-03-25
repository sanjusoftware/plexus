package com.bankengine.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String name;
    private String email;
    private List<String> roles;
    private String bank_id;
    private String bankName;
    private String sub;
    private String picture;
    private List<String> permissions;
}
