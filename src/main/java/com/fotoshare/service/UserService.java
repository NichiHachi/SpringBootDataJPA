package com.fotoshare.service;

import com.fotoshare.dto.UserDTO;
import com.fotoshare.dto.UserRegistrationDTO;
import com.fotoshare.entity.User;
import com.fotoshare.enums.Role;
import com.fotoshare.mapper.EntityMapper;
import com.fotoshare.repository.PhotoRepository;
import com.fotoshare.repository.AlbumRepository;
import com.fotoshare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for user management operations.
 * 
 * Responsibilities:
 * - User registration with validation and password hashing
 * - User lookup and search operations
 * - User account management (enable/disable, role changes)
 * - User statistics
 * 
 * This service follows the N-Tier architecture and handles all business
 * logic related to users, ensuring DTOs are used for external communication.
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityMapper entityMapper;

    public UserService(UserRepository userRepository,
                       PhotoRepository photoRepository,
                       AlbumRepository albumRepository,
                       PasswordEncoder passwordEncoder,
                       EntityMapper entityMapper) {
        this.userRepository = userRepository;
        this.photoRepository = photoRepository;
        this.albumRepository = albumRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityMapper = entityMapper;
    }

    // =========================================
    // USER REGISTRATION
    // =========================================

    /**
     * Register a new user.
     *
     * @param registrationDTO The registration data
     * @return The created user as DTO
     * @throws IllegalArgumentException If validation fails
     */
    public UserDTO registerUser(UserRegistrationDTO registrationDTO) {
        logger.info("Registering new user: {}", registrationDTO.getUsername());

        // Validate registration data
        validateRegistration(registrationDTO);

        // Create user entity
        User user = User.builder()
                .username(registrationDTO.getUsername().trim())
                .email(registrationDTO.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(registrationDTO.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        // Save and return
        User savedUser = userRepository.save(user);
        logger.info("User registered successfully: {} (ID: {})", savedUser.getUsername(), savedUser.getId());

        return entityMapper.toUserDTO(savedUser, 0L, 0L);
    }

    /**
     * Validate registration data.
     */
    private void validateRegistration(UserRegistrationDTO dto) {
        // Check password confirmation
        if (!dto.isPasswordMatching()) {
            throw new IllegalArgumentException("Les mots de passe ne correspondent pas");
        }

        // Check username uniqueness
        if (userRepository.existsByUsername(dto.getUsername().trim())) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà utilisé");
        }

        // Check email uniqueness
        if (userRepository.existsByEmail(dto.getEmail().trim().toLowerCase())) {
            throw new IllegalArgumentException("Cette adresse email est déjà utilisée");
        }
    }

    // =========================================
    // USER LOOKUP
    // =========================================

    /**
     * Find a user by ID.
     */
    @Transactional(readOnly = true)
    public Optional<UserDTO> findById(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    long photoCount = photoRepository.countByOwnerId(userId);
                    long albumCount = albumRepository.countByOwnerId(userId);
                    return entityMapper.toUserDTO(user, photoCount, albumCount);
                });
    }

    /**
     * Find a user by username.
     */
    @Transactional(readOnly = true)
    public Optional<UserDTO> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> {
                    long photoCount = photoRepository.countByOwnerId(user.getId());
                    long albumCount = albumRepository.countByOwnerId(user.getId());
                    return entityMapper.toUserDTO(user, photoCount, albumCount);
                });
    }

    /**
     * Find a user by email.
     */
    @Transactional(readOnly = true)
    public Optional<UserDTO> findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .map(entityMapper::toUserDTO);
    }

    /**
     * Get the User entity by ID (for internal service use only).
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserEntityById(Long userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get the User entity by username (for internal service use only).
     */
    @Transactional(readOnly = true)
    public Optional<User> findUserEntityByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // =========================================
    // USER LISTING AND SEARCH
    // =========================================

    /**
     * Get all users with pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> findAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(entityMapper::toUserDTO);
    }

    /**
     * Get all enabled users.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> findEnabledUsers(Pageable pageable) {
        return userRepository.findByEnabled(true, pageable)
                .map(entityMapper::toUserDTO);
    }

    /**
     * Get all disabled users.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> findDisabledUsers(Pageable pageable) {
        return userRepository.findByEnabled(false, pageable)
                .map(entityMapper::toUserDTO);
    }

    /**
     * Search users by username.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> searchByUsername(String username, Pageable pageable) {
        return userRepository.findByUsernameContainingIgnoreCase(username, pageable)
                .map(entityMapper::toUserDTO);
    }

    /**
     * Search users by username or email (for sharing functionality).
     */
    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String searchTerm) {
        return entityMapper.toUserDTOList(userRepository.searchUsers(searchTerm));
    }

    /**
     * Get all other users (excluding the specified user) for sharing.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> findOtherUsers(Long excludeUserId) {
        return entityMapper.toUserDTOList(userRepository.findAllOtherUsers(excludeUserId));
    }

    // =========================================
    // USER MANAGEMENT
    // =========================================

    /**
     * Enable a user account.
     */
    public boolean enableUser(Long userId) {
        logger.info("Enabling user account: {}", userId);
        int updated = userRepository.updateEnabledStatus(userId, true);
        return updated > 0;
    }

    /**
     * Disable (ban) a user account.
     */
    public boolean disableUser(Long userId) {
        logger.info("Disabling user account: {}", userId);
        int updated = userRepository.updateEnabledStatus(userId, false);
        return updated > 0;
    }

    /**
     * Toggle user enabled status.
     */
    public Optional<UserDTO> toggleUserStatus(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    user.setEnabled(!user.getEnabled());
                    User savedUser = userRepository.save(user);
                    logger.info("User {} status toggled to: {}", userId, savedUser.getEnabled());
                    return entityMapper.toUserDTO(savedUser);
                });
    }

    /**
     * Change a user's role.
     */
    public boolean changeUserRole(Long userId, Role newRole) {
        logger.info("Changing user {} role to: {}", userId, newRole);
        int updated = userRepository.updateUserRole(userId, newRole);
        return updated > 0;
    }

    /**
     * Update user profile information.
     */
    public Optional<UserDTO> updateProfile(Long userId, String email) {
        return userRepository.findById(userId)
                .map(user -> {
                    String normalizedEmail = email.trim().toLowerCase();

                    // Check if email is already used by another user
                    Optional<User> existingUser = userRepository.findByEmail(normalizedEmail);
                    if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                        throw new IllegalArgumentException("Cette adresse email est déjà utilisée");
                    }

                    user.setEmail(normalizedEmail);
                    User savedUser = userRepository.save(user);
                    logger.info("User {} profile updated", userId);
                    return entityMapper.toUserDTO(savedUser);
                });
    }

    /**
     * Change user password.
     */
    public boolean changePassword(Long userId, String currentPassword, String newPassword) {
        return userRepository.findById(userId)
                .map(user -> {
                    // Verify current password
                    if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                        throw new IllegalArgumentException("Le mot de passe actuel est incorrect");
                    }

                    // Update password
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    userRepository.save(user);
                    logger.info("User {} password changed", userId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Delete a user account.
     * Note: This will cascade delete all photos, albums, comments, and shares.
     */
    public boolean deleteUser(Long userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            logger.info("User {} deleted", userId);
            return true;
        }
        return false;
    }

    // =========================================
    // STATISTICS
    // =========================================

    /**
     * Get total user count.
     */
    @Transactional(readOnly = true)
    public long getTotalUserCount() {
        return userRepository.count();
    }

    /**
     * Get active user count.
     */
    @Transactional(readOnly = true)
    public long getActiveUserCount() {
        return userRepository.countByEnabledTrue();
    }

    /**
     * Get user count by role.
     */
    @Transactional(readOnly = true)
    public long getUserCountByRole(Role role) {
        return userRepository.countByRole(role);
    }

    // =========================================
    // VALIDATION
    // =========================================

    /**
     * Check if a username is available.
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username.trim());
    }

    /**
     * Check if an email is available.
     */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * Check if a user exists.
     */
    @Transactional(readOnly = true)
    public boolean userExists(Long userId) {
        return userRepository.existsById(userId);
    }
}