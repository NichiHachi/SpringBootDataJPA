package com.fotoshare.service;

import com.fotoshare.entity.User;
import com.fotoshare.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService implementation for Spring Security authentication.
 * 
 * This service bridges our User entity with Spring Security's authentication mechanism.
 * It loads user details from the database and converts them to Spring Security's UserDetails format.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load user by username for authentication.
     * This method is called by Spring Security during the authentication process.
     *
     * @param username The username to search for
     * @return UserDetails containing the user's authentication information
     * @throws UsernameNotFoundException If the user is not found
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Attempting to authenticate user: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("Authentication failed: User not found - {}", username);
                    return new UsernameNotFoundException("Utilisateur non trouvé: " + username);
                });

        if (!user.isActive()) {
            logger.warn("Authentication failed: Account disabled - {}", username);
            throw new UsernameNotFoundException("Ce compte a été désactivé");
        }

        logger.info("User authenticated successfully: {}", username);

        return new CustomUserDetails(user);
    }

    /**
     * Custom UserDetails implementation that wraps our User entity.
     * This provides the bridge between our domain model and Spring Security.
     */
    public static class CustomUserDetails implements UserDetails {

        private final User user;

        public CustomUserDetails(User user) {
            this.user = user;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            // Convert our Role enum to Spring Security's GrantedAuthority
            return Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
            );
        }

        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }

        @Override
        public String getUsername() {
            return user.getUsername();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return user.isActive();
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return user.isActive();
        }

        /**
         * Get the underlying User entity.
         * Useful for accessing user-specific data in controllers and services.
         */
        public User getUser() {
            return user;
        }

        /**
         * Get the user's ID.
         */
        public Long getUserId() {
            return user.getId();
        }

        /**
         * Get the user's email.
         */
        public String getEmail() {
            return user.getEmail();
        }

        /**
         * Check if the user has admin privileges.
         */
        public boolean isAdmin() {
            return user.isAdmin();
        }

        /**
         * Check if the user has moderator privileges.
         */
        public boolean isModerator() {
            return user.isModerator();
        }
    }
}