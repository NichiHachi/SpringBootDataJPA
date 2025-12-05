-- FotoShare Database Initialization Script
-- Application de Partage de Photos Sécurisée

CREATE DATABASE IF NOT EXISTS fotoshareDB;
USE fotoshareDB;

-- 1. Table Utilisateur (avec 'enabled' pour ban/désactivation)
CREATE TABLE IF NOT EXISTS utilisateur (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('USER', 'ADMIN', 'MODERATOR') DEFAULT 'USER',
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table Photo (avec storage_filename UUID et content_type)
CREATE TABLE IF NOT EXISTS photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    original_filename VARCHAR(255),
    storage_filename VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(50) NOT NULL,
    visibility ENUM('PRIVATE', 'PUBLIC') DEFAULT 'PRIVATE',
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES utilisateur(id) ON DELETE CASCADE,
    INDEX idx_photo_owner (owner_id),
    INDEX idx_photo_visibility (visibility)
);

-- 3. Table Album
CREATE TABLE IF NOT EXISTS album (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES utilisateur(id) ON DELETE CASCADE,
    INDEX idx_album_owner (owner_id)
);

-- 4. Table de liaison Album-Photo (Many-to-Many)
CREATE TABLE IF NOT EXISTS album_photo (
    album_id BIGINT NOT NULL,
    photo_id BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (album_id, photo_id),
    FOREIGN KEY (album_id) REFERENCES album(id) ON DELETE CASCADE,
    FOREIGN KEY (photo_id) REFERENCES photo(id) ON DELETE CASCADE
);

-- 5. Table Partage (avec contrainte d'unicité photo/user)
CREATE TABLE IF NOT EXISTS partage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    photo_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    permission_level ENUM('READ', 'COMMENT', 'ADMIN') DEFAULT 'READ',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (photo_id) REFERENCES photo(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES utilisateur(id) ON DELETE CASCADE,
    UNIQUE KEY uk_photo_user (photo_id, user_id),
    INDEX idx_partage_user (user_id),
    INDEX idx_partage_photo (photo_id)
);

-- 6. Table Commentaire
CREATE TABLE IF NOT EXISTS commentaire (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    text TEXT NOT NULL,
    photo_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (photo_id) REFERENCES photo(id) ON DELETE CASCADE,
    FOREIGN KEY (author_id) REFERENCES utilisateur(id) ON DELETE CASCADE,
    INDEX idx_commentaire_photo (photo_id),
    INDEX idx_commentaire_author (author_id)
);

-- Insertion d'un utilisateur admin par défaut
-- Mot de passe: Admin123! (hashé avec BCrypt)
INSERT INTO utilisateur (username, email, password_hash, role, enabled) VALUES
('admin', 'admin@fotoshare.com', '$2y$10$x9w1iR7vu3/jphkN0AHi6e336GCi4wkdrSl7gA6.tsbN0ePwlUe7e', 'ADMIN', TRUE)
ON DUPLICATE KEY UPDATE id=id;
