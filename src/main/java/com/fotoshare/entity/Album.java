package com.fotoshare.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity representing an album in the FotoShare application.
 * Albums allow users to organize their photos into collections.
 */
@Entity
@Table(name = "album", indexes = {
    @Index(name = "idx_album_owner", columnList = "owner_id")
})
@Data
@EqualsAndHashCode(exclude = {"photos", "owner"})
@ToString(exclude = {"photos", "owner"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToMany
    @JoinTable(
        name = "album_photo",
        joinColumns = @JoinColumn(name = "album_id"),
        inverseJoinColumns = @JoinColumn(name = "photo_id")
    )
    @Builder.Default
    private Set<Photo> photos = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Helper methods for managing photos
    public void addPhoto(Photo photo) {
        this.photos.add(photo);
        photo.getAlbums().add(this);
    }

    public void removePhoto(Photo photo) {
        this.photos.remove(photo);
        photo.getAlbums().remove(this);
    }

    public int getPhotoCount() {
        return this.photos.size();
    }
}