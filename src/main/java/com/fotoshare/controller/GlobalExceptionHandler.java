package com.fotoshare.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

/**
 * Global exception handler for the FotoShare application.
 * 
 * This class handles exceptions across all controllers and provides
 * user-friendly error pages and messages. Following the N-Tier architecture,
 * it belongs to the Presentation Layer and converts exceptions into
 * appropriate HTTP responses and views.
 * 
 * Features:
 * - Centralized exception handling
 * - User-friendly error messages
 * - Logging for debugging
 * - Redirect with flash messages for recoverable errors
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle IllegalArgumentException - typically validation errors.
     * Redirects back with an error message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex,
                                                   HttpServletRequest request,
                                                   RedirectAttributes redirectAttributes) {
        logger.warn("Validation error: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        
        // Try to redirect back to the referer, or to home if not available
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    /**
     * Handle IllegalStateException - typically application state errors.
     */
    @ExceptionHandler(IllegalStateException.class)
    public String handleIllegalStateException(IllegalStateException ex,
                                               HttpServletRequest request,
                                               RedirectAttributes redirectAttributes) {
        logger.error("Application state error: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        
        redirectAttributes.addFlashAttribute("errorMessage", 
                "Une erreur inattendue s'est produite. Veuillez réessayer.");
        
        return "redirect:/";
    }

    /**
     * Handle Access Denied - 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ex,
                                               HttpServletRequest request,
                                               Model model) {
        logger.warn("Access denied: {} - URL: {} - User: {}", 
                ex.getMessage(), 
                request.getRequestURI(),
                request.getRemoteUser());
        
        model.addAttribute("errorTitle", "Accès refusé");
        model.addAttribute("errorMessage", "Vous n'avez pas la permission d'accéder à cette ressource.");
        model.addAttribute("errorCode", "403");
        
        return "error/403";
    }

    /**
     * Handle file upload size exceeded.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
                                                        RedirectAttributes redirectAttributes) {
        logger.warn("File upload too large: {}", ex.getMessage());
        
        redirectAttributes.addFlashAttribute("errorMessage", 
                "Le fichier est trop volumineux. La taille maximale autorisée est de 10 MB.");
        
        return "redirect:/photos/upload";
    }

    /**
     * Handle IOException - typically file operation errors.
     */
    @ExceptionHandler(IOException.class)
    public String handleIOException(IOException ex,
                                     HttpServletRequest request,
                                     RedirectAttributes redirectAttributes) {
        logger.error("IO error: {} - URL: {}", ex.getMessage(), request.getRequestURI(), ex);
        
        redirectAttributes.addFlashAttribute("errorMessage", 
                "Une erreur s'est produite lors du traitement du fichier. Veuillez réessayer.");
        
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        return "redirect:/photos/my";
    }

    /**
     * Handle 404 - Resource not found.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFoundException(Exception ex,
                                           HttpServletRequest request,
                                           Model model) {
        logger.warn("Resource not found: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        
        model.addAttribute("errorTitle", "Page non trouvée");
        model.addAttribute("errorMessage", "La page que vous recherchez n'existe pas.");
        model.addAttribute("errorCode", "404");
        
        return "error/404";
    }

    /**
     * Handle ResourceNotFoundException - custom exception for entities not found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFoundException(ResourceNotFoundException ex,
                                                   HttpServletRequest request,
                                                   Model model) {
        logger.warn("Resource not found: {} - URL: {}", ex.getMessage(), request.getRequestURI());
        
        model.addAttribute("errorTitle", "Ressource non trouvée");
        model.addAttribute("errorMessage", ex.getMessage());
        model.addAttribute("errorCode", "404");
        
        return "error/404";
    }

    /**
     * Handle all other unhandled exceptions - 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGenericException(Exception ex,
                                          HttpServletRequest request,
                                          Model model) {
        logger.error("Unhandled exception: {} - URL: {} - Type: {}", 
                ex.getMessage(), 
                request.getRequestURI(),
                ex.getClass().getName(),
                ex);
        
        model.addAttribute("errorTitle", "Erreur serveur");
        model.addAttribute("errorMessage", 
                "Une erreur inattendue s'est produite. Notre équipe a été notifiée.");
        model.addAttribute("errorCode", "500");
        
        return "error/500";
    }

    /**
     * Custom exception for resource not found scenarios.
     */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }

        public ResourceNotFoundException(String resourceType, Long id) {
            super(String.format("%s avec l'identifiant %d n'existe pas", resourceType, id));
        }
    }
}