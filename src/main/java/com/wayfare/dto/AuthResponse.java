package com.wayfare.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private Long id;
    private String email;
    private String fullName;
    private String handle;
    private String avatar;
    private Set<String> roles;
}
