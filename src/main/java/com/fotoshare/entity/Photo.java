package com.fotoshare.entity;

import com.fotoshare.enums.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entité Photo - Représente une photo uploadée par un utilisateur.
 * 
 * Les fichiers images sont stockés sur le système de fichiers avec un nom UUID
 * pour éviter les collisions et les injections de noms de fichiers.
 * Seules les métadonnées sont stockées en base de données.
 */
@Entity
@Table(name = "photo", indexes = {
    @Index(name = "idx_photo_owner", columnList = "owner_id"),
    @Index(name = "idx_photo_visibility", columnList = "visibility")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Titre de la photo (obligatoire)
     */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /**
     * Description optionnelle de la photo
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Nom original du fichier uploadé (pour affichage)
     */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /**
     * Nom du fichier stocké sur le disque (UUID généré)
     */
    @Column(name = "storage_filename", nullable = false, unique = true, length = 255)
    private String storageFilename;

    /**
     * Type MIME du fichier (ex: image/jpeg, image/png)
     * Vérifié via Magic Numbers lors de l'upload
     */
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    /**
     * Niveau de visibilité de la photo
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    /**
     * Propriétaire de la photo
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    /**
     * Date de création
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Albums contenant cette photo (Many-to-Many)
     */
    @ManyToMany(mappedBy = "photos")
    @Builder.Default
    private Set<Album> albums = new HashSet<>();

    /**
     * Partages de cette photo avec d'autres utilisateurs
     */
    @OneToMany(mappedBy = "photo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Share> shares = new HashSet<>();

    /**
     * Commentaires sur cette photo
     */
    @OneToMany(mappedBy = "photo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Comment> comments = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.visibility == null) {
            this.visibility = Visibility.PRIVATE;
        }
    }

    /**
     * Vérifie si l'utilisateur donné est le propriétaire de cette photo.
     */
    public boolean isOwner(User user) {
        return user != null && this.owner != null && this.owner.getId().equals(user.getId());
    }

    /**
     * Vérifie si la photo est publique.
     */
    public boolean isPublic() {
        return this.visibility == Visibility.PUBLIC;
    }

    /**
     * Ajoute un partage pour cette photo.
     */
    public void addShare(Share share) {
        shares.add(share);
        share.setPhoto(this);
    }

    /**
     * Supprime un partage de cette photo.
     */
    public void removeShare(Share share) {
        shares.remove(share);
        share.setPhoto(null);
    }

    /**
     * Ajoute un commentaire à cette photo.
     */
    public void addComment(Comment comment) {
        comments.add(comment);
        comment.setPhoto(this);
    }

    /**
     * Supprime un commentaire de cette photo.
     */
    public void removeComment(Comment comment) {
        comments.remove(comment);
        comment.setPhoto(null);
    }
}