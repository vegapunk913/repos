package com.example.exam_java.server;

import com.example.exam_java.dao.MessageDao;
import com.example.exam_java.dao.UserDao;
import com.example.exam_java.entity.*;
import com.example.exam_java.protocol.Protocol;
import com.example.exam_java.util.PasswordUtil;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gère un client connecté (RG11: chaque client dans un thread séparé)
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final UserDao userDao;
    private final MessageDao messageDao;
    private final ConcurrentHashMap<String, ClientHandler> onlineUsers;
    private final MessagerieServer server;

    private BufferedReader reader;
    private PrintWriter writer;
    private User currentUser;

    public ClientHandler(Socket socket, UserDao userDao, MessageDao messageDao,
                         ConcurrentHashMap<String, ClientHandler> onlineUsers, MessagerieServer server) {
        this.socket = socket;
        this.userDao = userDao;
        this.messageDao = messageDao;
        this.onlineUsers = onlineUsers;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            String line;
            while ((line = reader.readLine()) != null) {
                handleCommand(line);
            }
        } catch (IOException e) {
            server.log("Client déconnecté: " + (currentUser != null ? currentUser.getUsername() : "inconnu"));
        } finally {
            disconnect();
        }
    }

    private void handleCommand(String line) {
        if (line == null || (line = line.trim()).isEmpty()) return;
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;

        String cmd = parts[0].trim();

        switch (cmd) {
            case Protocol.REGISTER -> handleRegister(parts);
            case Protocol.LOGIN -> handleLogin(parts);
            case Protocol.LOGOUT -> handleLogout();
            case Protocol.SEND -> handleSend(parts);
            case Protocol.SEND_FILE -> handleSendFile(parts);
            case Protocol.REQUEST_FILE -> handleRequestFile(parts);
            case Protocol.LIST_ONLINE -> handleListOnline();
            case Protocol.LIST_ALL_CONTACTS -> handleListAllContacts();
            case Protocol.LIST_MEMBERS -> handleListMembers();
            case Protocol.LIST_PENDING -> handleListPending();
            case Protocol.VALIDATE -> handleValidate(parts);
            case Protocol.GET_HISTORY -> handleGetHistory(parts);
            case Protocol.ADMIN_LIST_USERS -> handleAdminListUsers();
            case Protocol.ADMIN_CREATE_USER -> handleAdminCreateUser(parts);
            case Protocol.ADMIN_BLOCK_USER -> handleAdminBlockUser(parts);
            case Protocol.ADMIN_UNBLOCK_USER -> handleAdminUnblockUser(parts);
            case Protocol.ADMIN_DELETE_USER -> handleAdminDeleteUser(parts);
            default -> send(Protocol.ERROR, "Commande inconnue");
        }
    }

    private void handleRegister(String[] parts) {
        if (parts.length < 4) {
            send(Protocol.REGISTER_FAIL, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        String password = parts[2];
        String roleStr = parts[3];

        if (userDao.existsByUsername(username)) {
            send(Protocol.REGISTER_FAIL, "Username déjà utilisé (RG1)");
            return;
        }

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            send(Protocol.REGISTER_FAIL, "Rôle invalide: ORGANISATEUR, MEMBRE ou BENEVOLE");
            return;
        }

        User user = new User(username, PasswordUtil.hash(password), role);
        user.setValidated(false);  // En attente de validation par l'admin
        userDao.insert(user);
        server.log("Inscription: " + username + " (" + role + ") - en attente de validation");
        send(Protocol.REGISTER_SUCCESS);
    }

    private void handleLogin(String[] parts) {
        if (parts.length < 3) {
            send(Protocol.LOGIN_FAIL, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        String password = parts[2];

        User user = userDao.findByUsername(username);
        if (user == null) {
            send(Protocol.LOGIN_FAIL, "Ce compte n'existe pas.");
            return;
        }
        if (!PasswordUtil.verify(password, user.getPassword())) {
            send(Protocol.LOGIN_FAIL, "Identifiants incorrects");
            return;
        }
        // Compte inactif = non validé ou bloqué (un seul message)
        if (!"admin".equals(username) && (!user.isValidated() || user.isBlocked())) {
            send(Protocol.LOGIN_FAIL, "Compte inactif. Veuillez contacter l'administrateur.");
            return;
        }

        // RG3: un utilisateur ne peut être connecté qu'une seule fois
        if (onlineUsers.containsKey(username)) {
            send(Protocol.LOGIN_FAIL, "Déjà connecté ailleurs");
            return;
        }

        currentUser = user;
        user.setStatus(UserStatus.ONLINE);
        userDao.update(user);
        onlineUsers.put(username, this);

        server.log("Connexion: " + username + " (RG12)");
        send(Protocol.LOGIN_SUCCESS, username, user.getRole().name());

        // RG6: livrer les messages en attente
        deliverPendingMessages(user);

        // Notifier les autres de la connexion
        broadcastUserStatus(username, true);
    }

    private void deliverPendingMessages(User user) {
        List<Message> pending = messageDao.findPendingForUser(user);
        for (Message m : pending) {
            if (m.isFile()) sendFileMessage(m);
            else sendMessage(m);
            messageDao.markAsReceived(m);
        }
    }

    private void handleLogout() {
        disconnect();
    }

    private void handleSend(String[] parts) {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (parts.length < 3) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String receiverName = parts[1];
        String contenu = String.join(Protocol.SEP, java.util.Arrays.copyOfRange(parts, 2, parts.length));

        // RG7: contenu non vide et max 1000 caractères
        if (contenu == null || contenu.isBlank()) {
            send(Protocol.ERROR, "Message vide (RG7)");
            return;
        }
        if (contenu.length() > 1000) {
            send(Protocol.ERROR, "Message trop long (max 1000 caractères)");
            return;
        }

        User receiver = userDao.findByUsername(receiverName);
        if (receiver == null) {
            send(Protocol.ERROR, "Destinataire inexistant (RG5)");
            return;
        }

        // RG5: expéditeur doit être connecté
        Message message = new Message(currentUser, receiver, contenu);
        messageDao.insert(message);

        server.log("Message envoyé: " + currentUser.getUsername() + " -> " + receiverName + " (RG12)");

        ClientHandler receiverHandler = onlineUsers.get(receiverName);
        if (receiverHandler != null) {
            // Destinataire en ligne: livraison immédiate
            receiverHandler.sendMessage(message);
            messageDao.markAsReceived(message);
        }
        // Sinon: RG6 - message enregistré, livré à la prochaine connexion

        send(Protocol.OK, "Message envoyé");
    }

    private void handleSendFile(String[] parts) {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (parts.length < 4) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String receiverName = parts[1];
        String fileName = parts[2];
        String base64Content = String.join(Protocol.SEP, java.util.Arrays.copyOfRange(parts, 3, parts.length));

        User receiver = userDao.findByUsername(receiverName);
        if (receiver == null) {
            send(Protocol.ERROR, "Destinataire inexistant");
            return;
        }

        try {
            byte[] fileBytes = Base64.getDecoder().decode(base64Content);
            if (fileBytes.length > 5 * 1024 * 1024) { // 5 MB max
                send(Protocol.ERROR, "Fichier trop volumineux (max 5 Mo)");
                return;
            }

            Path uploadDir = Path.of("uploads").resolve(currentUser.getUsername());
            Files.createDirectories(uploadDir);
            String safeName = System.currentTimeMillis() + "_" + fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = uploadDir.resolve(safeName);
            Files.write(filePath, fileBytes);

            Message message = new Message(currentUser, receiver, "[FICHIER] " + fileName);
            message.setFile(true);
            message.setFileName(fileName);
            message.setFilePath(filePath.toString());
            messageDao.insert(message);

            server.log("Fichier envoyé: " + fileName + " " + currentUser.getUsername() + " -> " + receiverName);

            ClientHandler receiverHandler = onlineUsers.get(receiverName);
            if (receiverHandler != null) {
                receiverHandler.sendFileMessage(message);
                messageDao.markAsReceived(message);
            }
            send(Protocol.OK, "Fichier envoyé");
        } catch (Exception e) {
            send(Protocol.ERROR, "Erreur envoi fichier: " + e.getMessage());
        }
    }

    private void sendFileMessage(Message m) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String payload = m.getSender().getUsername() + ":" + m.getDateEnvoi().format(fmt) + ":[FILE]" + m.getId() + "|" + m.getFileName();
        send(Protocol.MESSAGE, payload);
    }

    private void handleRequestFile(String[] parts) {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (parts.length < 2) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        long messageId = Long.parseLong(parts[1]);
        Message msg = messageDao.findById(messageId);
        if (msg == null || !msg.isFile()) {
            send(Protocol.ERROR, "Fichier introuvable");
            return;
        }
        if (msg.getReceiver().getId().equals(currentUser.getId()) || msg.getSender().getId().equals(currentUser.getId())) {
            try {
                byte[] content = Files.readAllBytes(Path.of(msg.getFilePath()));
                String base64 = Base64.getEncoder().encodeToString(content);
                send(Protocol.FILE_DATA, String.valueOf(messageId), msg.getFileName(), base64);
            } catch (IOException e) {
                send(Protocol.ERROR, "Erreur lecture fichier");
            }
        } else {
            send(Protocol.ERROR, "Accès refusé");
        }
    }

    private void handleListOnline() {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        List<String> online = onlineUsers.keySet().stream()
                .filter(u -> !u.equals(currentUser.getUsername()))
                .sorted()
                .toList();
        send(Protocol.LIST_ONLINE, String.join(",", online));
    }

    private void handleListAllContacts() {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        List<User> members = userDao.findAllMembers();
        StringBuilder sb = new StringBuilder();
        for (User u : members) {
            if (u.getUsername().equals(currentUser.getUsername())) continue;
            if (sb.length() > 0) sb.append(";");
            boolean online = onlineUsers.containsKey(u.getUsername());
            sb.append(u.getUsername()).append(":").append(online ? "ONLINE" : "OFFLINE").append(":").append(u.getRole());
        }
        send(Protocol.CONTACTS, sb.toString());
    }

    private void handleListMembers() {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        // RG13: seul ORGANISATEUR peut consulter la liste complète
        if (currentUser.getRole() != Role.ORGANISATEUR) {
            send(Protocol.ERROR, "Accès réservé aux organisateurs");
            return;
        }
        List<User> members = userDao.findAllMembers();
        StringBuilder sb = new StringBuilder();
        for (User u : members) {
            if (sb.length() > 0) sb.append(";");
            sb.append(u.getUsername()).append(":").append(u.getRole()).append(":").append(u.getStatus())
                    .append(":").append(u.isValidated() ? "VALIDATED" : "PENDING");
        }
        send(Protocol.MEMBERS, sb.toString());
    }

    private void handleListPending() {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (!"admin".equals(currentUser.getUsername())) {
            send(Protocol.ERROR, "Réservé à l'administrateur");
            return;
        }
        List<User> pending = userDao.findPendingUsers();
        StringBuilder sb = new StringBuilder();
        for (User u : pending) {
            if (sb.length() > 0) sb.append(";");
            sb.append(u.getUsername()).append(":").append(u.getRole()).append(":").append(u.getDateCreation());
        }
        send(Protocol.PENDING_USERS, sb.toString());
    }

    private void handleValidate(String[] parts) {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (!"admin".equals(currentUser.getUsername())) {
            send(Protocol.ERROR, "Réservé à l'administrateur");
            return;
        }
        if (parts.length < 2) {
            send(Protocol.VALIDATE_FAIL, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        User user = userDao.findByUsername(username);
        if (user == null) {
            send(Protocol.VALIDATE_FAIL, "Utilisateur inexistant");
            return;
        }
        if (user.isValidated()) {
            send(Protocol.VALIDATE_FAIL, "Déjà validé");
            return;
        }
        userDao.validateUser(username);
        server.log("Validation: " + username + " par admin");
        send(Protocol.VALIDATE_SUCCESS, username);
    }

    private void handleGetHistory(String[] parts) {
        if (currentUser == null) {
            send(Protocol.ERROR, "Non authentifié (RG2)");
            return;
        }
        if (parts.length < 2) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String otherUsername = parts[1];
        User other = userDao.findByUsername(otherUsername);
        if (other == null) {
            send(Protocol.ERROR, "Utilisateur inexistant");
            return;
        }

        List<Message> history = messageDao.findConversation(currentUser, other);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        StringBuilder sb = new StringBuilder();
        for (Message m : history) {
            if (sb.length() > 0) sb.append("||");
            String content = m.isFile() ? "[FILE]" + m.getId() + "|" + m.getFileName() : (m.getContenu() != null ? m.getContenu().replace(":", "::").replace("||", "|||") : "");
            sb.append(m.getSender().getUsername()).append(":")
                    .append(m.getDateEnvoi().format(fmt)).append(":")
                    .append(content);
        }
        send(Protocol.HISTORY, otherUsername, sb.toString());
    }

    private void sendMessage(Message m) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String payload = m.getSender().getUsername() + ":" + m.getDateEnvoi().format(fmt) + ":" + m.getContenu();
        send(Protocol.MESSAGE, payload);
    }

    private void broadcastUserStatus(String username, boolean online) {
        if (online) {
            for (ClientHandler h : onlineUsers.values()) {
                if (h != this && h.currentUser != null) {
                    h.send(Protocol.USER_ONLINE, username);
                }
            }
        } else {
            for (ClientHandler h : onlineUsers.values()) {
                if (h != this && h.currentUser != null) {
                    h.send(Protocol.USER_OFFLINE, username);
                }
            }
        }
    }

    public void send(String... parts) {
        if (writer != null) {
            writer.println(Protocol.build(parts).trim());
        }
    }

    private void disconnect() {
        if (currentUser != null) {
            String username = currentUser.getUsername();
            currentUser.setStatus(UserStatus.OFFLINE);
            userDao.update(currentUser);
            onlineUsers.remove(username);
            broadcastUserStatus(username, false);
            server.log("Déconnexion: " + username + " (RG12)");
            currentUser = null;
        }
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    public User getCurrentUser() {
        return currentUser;
    }

    /** Déconnecte forcément ce client (ex: compte bloqué/supprimé par l'admin). */
    public void forceDisconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private void ensureAdmin() {
        if (currentUser == null || !"admin".equals(currentUser.getUsername())) {
            send(Protocol.ERROR, "Réservé à l'administrateur");
            return;
        }
    }

    private void handleAdminListUsers() {
        ensureAdmin();
        if (currentUser == null) return;
        List<User> users = userDao.findAll();
        StringBuilder sb = new StringBuilder();
        for (User u : users) {
            if (sb.length() > 0) sb.append(";");
            boolean online = onlineUsers.containsKey(u.getUsername());
            sb.append(u.getUsername()).append(":").append(u.getRole()).append(":")
                    .append(u.isValidated()).append(":").append(u.isBlocked()).append(":").append(online);
        }
        send(Protocol.USERS_LIST, sb.toString());
    }

    private void handleAdminCreateUser(String[] parts) {
        ensureAdmin();
        if (currentUser == null) return;
        if (parts.length < 4) {
            send(Protocol.ERROR, "Paramètres manquants: ADMIN_CREATE_USER|username|password|role");
            return;
        }
        String username = parts[1];
        String password = parts[2];
        String roleStr = parts[3];
        if (userDao.existsByUsername(username)) {
            send(Protocol.ERROR, "Ce nom d'utilisateur existe déjà");
            return;
        }
        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            send(Protocol.ERROR, "Rôle invalide: ORGANISATEUR, MEMBRE ou BENEVOLE");
            return;
        }
        User user = new User(username, PasswordUtil.hash(password), role);
        user.setValidated(true);
        userDao.insert(user);
        server.log("Admin a créé le compte: " + username);
        send(Protocol.USER_CREATED, username);
    }

    private void handleAdminBlockUser(String[] parts) {
        ensureAdmin();
        if (currentUser == null) return;
        if (parts.length < 2) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        if ("admin".equals(username)) {
            send(Protocol.ERROR, "Impossible de bloquer l'administrateur");
            return;
        }
        User user = userDao.findByUsername(username);
        if (user == null) {
            send(Protocol.ERROR, "Utilisateur inexistant");
            return;
        }
        userDao.blockUser(username);
        ClientHandler other = onlineUsers.get(username);
        if (other != null) other.forceDisconnect();
        server.log("Admin a bloqué: " + username);
        send(Protocol.USER_BLOCKED, username);
    }

    private void handleAdminUnblockUser(String[] parts) {
        ensureAdmin();
        if (currentUser == null) return;
        if (parts.length < 2) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        userDao.unblockUser(username);
        server.log("Admin a débloqué: " + username);
        send(Protocol.USER_UNBLOCKED, username);
    }

    private void handleAdminDeleteUser(String[] parts) {
        ensureAdmin();
        if (currentUser == null) return;
        if (parts.length < 2) {
            send(Protocol.ERROR, "Paramètres manquants");
            return;
        }
        String username = parts[1];
        if ("admin".equals(username)) {
            send(Protocol.ERROR, "Impossible de supprimer l'administrateur");
            return;
        }
        if (userDao.findByUsername(username) == null) {
            send(Protocol.ERROR, "Utilisateur inexistant");
            return;
        }
        messageDao.deleteByUsername(username);
        boolean deleted = userDao.deleteUserByUsername(username);
        if (!deleted) {
            send(Protocol.ERROR, "Erreur lors de la suppression");
            return;
        }
        ClientHandler other = onlineUsers.get(username);
        if (other != null) other.forceDisconnect();
        server.log("Admin a supprimé le compte: " + username);
        send(Protocol.USER_DELETED, username);
    }
}
