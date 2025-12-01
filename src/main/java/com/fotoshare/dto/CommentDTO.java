package com.fotoshare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for transferring comment data between layers.
 * Used to avoid exposing JPA entities in the presentation layer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {

    private Long id;

    @NotBlank(message = "Le commentaire ne peut pas être vide")
    @Size(min = 1, max = 2000, message = "Le commentaire doit contenir entre 1 et 2000 caractères")
    private String text;

    private Long photoId;

    private Long authorId;

    private String authorUsername;

    private LocalDateTime createdAt;
}