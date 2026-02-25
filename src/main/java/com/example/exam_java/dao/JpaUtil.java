package com.example.exam_java.dao;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralise la création de l'EntityManagerFactory.
 * Surcharge possible via variables d'environnement (JDBC_URL, DB_USER, DB_PASSWORD).
 */
public final class JpaUtil {

    private static EntityManagerFactory emf;

    public static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            String jdbcUrl = System.getenv("JDBC_URL");
            if (jdbcUrl != null && !jdbcUrl.isBlank()) {
                Map<String, Object> overrides = new HashMap<>();
                overrides.put("jakarta.persistence.jdbc.url", jdbcUrl);
                String user = System.getenv("DB_USER");
                if (user != null && !user.isBlank()) {
                    overrides.put("jakarta.persistence.jdbc.user", user);
                }
                String password = System.getenv("DB_PASSWORD");
                if (password != null) {
                    overrides.put("jakarta.persistence.jdbc.password", password);
                }
                emf = Persistence.createEntityManagerFactory("PERSISTENCE", overrides);
            } else {
                emf = Persistence.createEntityManagerFactory("PERSISTENCE");
            }
        }
        return emf;
    }
}
