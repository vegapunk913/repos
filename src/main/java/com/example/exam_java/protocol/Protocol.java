package com.example.exam_java.protocol;

/**
 * Protocole de communication client-serveur.
 * Format: COMMAND[:param1][:param2]...
 * Les messages utilisent | comme séparateur pour éviter les conflits avec le contenu.
 */
public final class Protocol {

    // Commandes client -> serveur
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String SEND = "SEND";
    public static final String SEND_FILE = "SEND_FILE";
    public static final String REQUEST_FILE = "REQUEST_FILE";
    public static final String LIST_ONLINE = "LIST_ONLINE";
    public static final String LIST_MEMBERS = "LIST_MEMBERS";  // RG13: ORGANISATEUR uniquement
    public static final String LIST_ALL_CONTACTS = "LIST_ALL_CONTACTS";  // Tous les users avec statut (online/offline)
    public static final String LIST_PENDING = "LIST_PENDING";  // Admin: utilisateurs en attente de validation
    public static final String VALIDATE = "VALIDATE";           // Admin: valider un utilisateur
    public static final String GET_HISTORY = "GET_HISTORY";
    // Admin: gestion des utilisateurs (réservé à l'admin)
    public static final String ADMIN_LIST_USERS = "ADMIN_LIST_USERS";
    public static final String ADMIN_CREATE_USER = "ADMIN_CREATE_USER";
    public static final String ADMIN_BLOCK_USER = "ADMIN_BLOCK_USER";
    public static final String ADMIN_UNBLOCK_USER = "ADMIN_UNBLOCK_USER";
    public static final String ADMIN_DELETE_USER = "ADMIN_DELETE_USER";

    // Réponses serveur -> client
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL = "REGISTER_FAIL";
    public static final String MESSAGE = "MESSAGE";
    public static final String FILE_DATA = "FILE_DATA";
    public static final String USER_ONLINE = "USER_ONLINE";
    public static final String USER_OFFLINE = "USER_OFFLINE";
    public static final String CONTACTS = "CONTACTS";  // Liste tous les users avec statut
    public static final String HISTORY = "HISTORY";
    public static final String MEMBERS = "MEMBERS";
    public static final String PENDING_USERS = "PENDING_USERS";
    public static final String VALIDATE_SUCCESS = "VALIDATE_SUCCESS";
    public static final String VALIDATE_FAIL = "VALIDATE_FAIL";
    public static final String USERS_LIST = "USERS_LIST";       // Liste pour admin: username:role:validated:blocked:online
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_BLOCKED = "USER_BLOCKED";
    public static final String USER_UNBLOCKED = "USER_UNBLOCKED";
    public static final String USER_DELETED = "USER_DELETED";

    public static final String SEP = "|";
    public static final String END = "\n";

    public static String build(String... parts) {
        return String.join(SEP, parts) + END;
    }

    public static String[] parse(String line) {
        if (line == null || line.isBlank()) return new String[0];
        return line.trim().split("\\" + SEP);
    }
}
