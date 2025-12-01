package com.fotoshare.enums;

/**
 * Niveaux de permission pour le partage de photos.
 * Utilisé dans la table 'partage' pour définir les droits d'accès.
 */
public enum PermissionLevel {
    /**
     * Lecture seule - L'utilisateur peut voir la photo
     */
    READ,
    
    /**
     * Commentaire - L'utilisateur peut voir et commenter la photo
     */
    COMMENT,
    
    /**
     * Administration - L'utilisateur peut gérer la photo (modifier, supprimer)
     */
    ADMIN
}