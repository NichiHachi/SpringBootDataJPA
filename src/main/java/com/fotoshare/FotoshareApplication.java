package com.fotoshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * FotoShare - Application de Partage de Photos Sécurisée
 * 
 * Main entry point for the Spring Boot application.
 * This application follows N-Tier architecture:
 * - Presentation Layer (Controllers + DTOs)
 * - Business Layer (Services)
 * - Data Access Layer (Repositories)
 * - Data Layer (MariaDB + File Storage)
 */
@SpringBootApplication
@EnableAsync
public class FotoshareApplication {

    public static void main(String[] args) {
        SpringApplication.run(FotoshareApplication.class, args);
    }
}