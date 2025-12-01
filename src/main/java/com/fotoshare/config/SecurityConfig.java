package com.fotoshare.config;

import com.fotoshare.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Spring Security Configuration for FotoShare application.
 * 
 * This configuration implements:
 * - URL-based security with role-based access control
 * - BCrypt password encoding
 * - CSRF protection (enabled by default)
 * - Custom login/logout handling
 * - Method-level security with @PreAuthorize
 * 
 * Security Rules:
 * - Static resources (/css, /js, /images): permitAll
 * - Authentication endpoints (/login, /register): permitAll
 * - Admin endpoints (/admin/**): ADMIN role only
 * - All other endpoints: authenticated users only
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Password encoder using BCrypt.
     * BCrypt automatically handles salt generation and is resistant to rainbow table attacks.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication provider that uses our custom UserDetailsService.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Authentication manager bean for programmatic authentication.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Main security filter chain configuration.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Configure authentication provider
            .authenticationProvider(authenticationProvider())
            
            // URL-based authorization rules
            .authorizeHttpRequests(authorize -> authorize
                // Static resources - publicly accessible
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                
                // Public pages
                .requestMatchers("/", "/home", "/gallery").permitAll()
                
                // Authentication endpoints - accessible to non-authenticated users
                .requestMatchers("/login", "/register", "/forgot-password").permitAll()
                
                // Health check endpoint for Docker/Nginx
                .requestMatchers("/actuator/health").permitAll()
                
                // Public photo viewing (visibility check done in controller)
                .requestMatchers("/photos/view/*/image", "/photos/view/*/thumbnail").permitAll()
                
                // Admin endpoints - ADMIN role only
                .requestMatchers("/admin/**").hasRole("ADMIN")
                
                // Moderator endpoints
                .requestMatchers("/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Form login configuration
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/photos/my", true)
                .failureUrl("/login?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(authenticationSuccessHandler())
                .defaultSuccessUrl("/photos/my", true)
                .permitAll()
            )
            
            // Logout configuration
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .permitAll()
            )
            
            // Remember me configuration
            .rememberMe(remember -> remember
                .key("fotoshare-remember-me-key")
                .tokenValiditySeconds(86400 * 7) // 7 days
                .userDetailsService(userDetailsService)
            )
            
            // Session management
            .sessionManagement(session -> session
                .maximumSessions(3) // Allow up to 3 concurrent sessions
                .expiredUrl("/login?expired=true")
            )
            
            // Exception handling
            .exceptionHandling(exceptions -> exceptions
                .accessDeniedPage("/error/403")
            );

        // CSRF is enabled by default in Spring Security 6
        // No need to explicitly enable it

        return http.build();
    }

    /**
     * Custom success handler for redirecting users after login.
     * Uses SimpleUrlAuthenticationSuccessHandler which properly handles X-Forwarded-* headers.
     */
    @Bean
    public SimpleUrlAuthenticationSuccessHandler authenticationSuccessHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/photos/my");
        handler.setAlwaysUseDefaultTargetUrl(true);
        return handler;
    }

    /**
     * ForwardedHeaderFilter to properly handle X-Forwarded-* headers from reverse proxy.
     * This ensures redirects use the correct host, port, and protocol.
     */
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}