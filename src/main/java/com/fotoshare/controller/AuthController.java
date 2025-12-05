package com.fotoshare.controller;

import com.fotoshare.dto.UserRegistrationDTO;
import com.fotoshare.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for authentication operations (login, registration).
 * 
 * This controller handles:
 * - User login page display
 * - User registration with validation
 * - Password validation (complexity, matching confirmation)
 * 
 * All authentication logic is delegated to Spring Security and UserService.
 */
@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Display the login page.
     * 
     * @param error If present, indicates a failed login attempt
     * @param logout If present, indicates the user has logged out
     * @param expired If present, indicates the session has expired
     * @param model The view model
     * @return The login view name
     */
    @GetMapping("/login")
    public String showLoginPage(
            @ModelAttribute("error") String error,
            @ModelAttribute("logout") String logout,
            @ModelAttribute("expired") String expired,
            Model model) {
        
        if (error != null && !error.isEmpty()) {
            model.addAttribute("errorMessage", "Nom d'utilisateur ou mot de passe incorrect");
        }
        
        if (logout != null && !logout.isEmpty()) {
            model.addAttribute("successMessage", "Vous avez été déconnecté avec succès");
        }
        
        if (expired != null && !expired.isEmpty()) {
            model.addAttribute("warningMessage", "Votre session a expiré. Veuillez vous reconnecter.");
        }
        
        return "login";
    }

    /**
     * Display the registration page.
     * 
     * @param model The view model
     * @return The registration view name
     */
    @GetMapping("/register")
    public String showRegistrationPage(Model model) {
        if (!model.containsAttribute("registrationDTO")) {
            model.addAttribute("registrationDTO", new UserRegistrationDTO());
        }
        return "register";
    }

    /**
     * Process user registration.
     * 
     * Validates:
     * - All required fields are present
     * - Email format is valid
     * - Password meets complexity requirements
     * - Password confirmation matches
     * - Username and email are not already in use
     * 
     * @param registrationDTO The registration form data
     * @param bindingResult Validation results
     * @param redirectAttributes For flash attributes
     * @return Redirect to login on success, or back to registration on error
     */
    @PostMapping("/register")
    public String registerUser(
            @Valid @ModelAttribute("registrationDTO") UserRegistrationDTO registrationDTO,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        
        logger.info("Processing registration for: {}", registrationDTO.getUsername());

        // Check for validation errors
        if (bindingResult.hasErrors()) {
            logger.warn("Registration validation failed for: {}", registrationDTO.getUsername());
            bindingResult.getAllErrors().forEach(error -> {
                logger.warn("Validation error: {}", error.getDefaultMessage());
            });
            return "register";
        }

        // Check if passwords match
        if (!registrationDTO.isPasswordMatching()) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword",
                    "Les mots de passe ne correspondent pas");
            return "register";
        }

        try {
            // Register the user
            userService.registerUser(registrationDTO);
            
            logger.info("User registered successfully: {}", registrationDTO.getUsername());
            
            // Redirect to login with success message
            redirectAttributes.addFlashAttribute("successMessage",
                    "Inscription réussie ! Vous pouvez maintenant vous connecter.");
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            // Handle business validation errors (e.g., username/email already exists)
            logger.warn("Registration failed for {}: {}", registrationDTO.getUsername(), e.getMessage());
            
            if (e.getMessage().contains("nom d'utilisateur")) {
                bindingResult.rejectValue("username", "error.username", e.getMessage());
            } else if (e.getMessage().contains("email")) {
                bindingResult.rejectValue("email", "error.email", e.getMessage());
            } else {
                bindingResult.reject("error.global", e.getMessage());
            }
            
            return "register";
            
        } catch (Exception e) {
            logger.error("Unexpected error during registration for {}", registrationDTO.getUsername(), e);
            bindingResult.reject("error.global", "Une erreur inattendue s'est produite. Veuillez réessayer.");
            return "register";
        }
    }

    /**
     * Display the forgot password page.
     * (Placeholder for future implementation)
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordPage() {
        return "forgot-password";
    }
}