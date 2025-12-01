package com.fotoshare.dto;

import com.fotoshare.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for User entity.
 * Used to transfer user data between layers without exposing entity internals.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private Role role;
    private Boolean enabled;
    private LocalDateTime createdAt;
    
    // Statistics
    private Long photoCount;
    private Long albumCount;
}