package com.example.exam_java.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utilitaire pour le hachage des mots de passe (RG9)
 */
public final class PasswordUtil {

    private static final int WORK_FACTOR = 10;

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORK_FACTOR));
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
