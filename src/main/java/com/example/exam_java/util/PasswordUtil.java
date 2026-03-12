package com.example.exam_java.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilitaire pour le hachage des mots de passe (RG9)
 */
public final class PasswordUtil {

    private static final int WORK_FACTOR = 10;

    /**
     * Hache un mot de passe en clair avec BCrypt (RG9). À utiliser avant stockage en base.
     * Paramètre : plainPassword – mot de passe en clair. Retourne la chaîne hashée (jamais null).
     */
    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    /**
     * Vérifie qu'un mot de passe en clair correspond au hash stocké.
     * Paramètres : plainPassword – saisi par l'utilisateur ; hashedPassword – stocké en BDD. Retourne true si correspondance, false sinon.
     */
    public static boolean verify(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
