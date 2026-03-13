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
    /**
     * Charge le pilote PostgreSQL. Lève IllegalStateException si le driver est introuvable.
     * Aucun paramètre. Ne renvoie rien.
     */
    private static void ensureDriverLoaded() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Pilote PostgreSQL introuvable. Vérifiez la dépendance postgresql dans pom.xml.", e);
        }
    }

    /**
     * Retourne l'EntityManagerFactory JPA (singleton). Au premier appel, crée la factory en utilisant persistence.xml ;
     * les variables d'environnement JDBC_URL, DB_USER, DB_PASSWORD peuvent surcharger la config.
     * Aucun paramètre. Retourne l'EntityManagerFactory. Lève une exception si la BDD est inaccessible.
     */
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
