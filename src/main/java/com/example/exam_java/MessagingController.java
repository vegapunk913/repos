package com.example.exam_java;

import com.example.exam_java.entity.Role;
import com.example.exam_java.model.ContactItem;
import com.example.exam_java.network.ServerConnection;
import com.example.exam_java.protocol.Protocol;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Callback;

import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur de l'écran de messagerie : liste des contacts, conversation, envoi de messages et de fichiers,
 * historique, déconnexion et accès à l'administration (admin) ou à la liste des membres (organisateur).
 */
public class MessagingController {

    @FXML private ScrollPane scrollPane;
    @FXML private VBox messageContainer;
    @FXML private Label lbl_user_role;
    @FXML private Label lbl_status_dot;
    @FXML private Label lbl_online_count;
    @FXML private Label lbl_section_online;
    @FXML private Label lbl_section_offline;
    @FXML private Label lbl_conversation;
    @FXML private Label lbl_error;
    @FXML private ListView<ContactItem> list_contacts_online;
    @FXML private ListView<ContactItem> list_contacts_offline;
    @FXML private TextField txt_message;
    @FXML private Button btn_list_members;
    @FXML private Button btn_administration;
    @FXML private VBox placeholderPane;
    @FXML private VBox conversationPane;
    @FXML private HBox inputArea;

    private ServerConnection connection;
    private String username;
    private Role role;
    private String selectedUser;
    private Label lastCheckLabel = null;
    private final ObservableList<ContactItem> contactsOnline = FXCollections.observableArrayList();
    private final ObservableList<ContactItem> contactsOffline = FXCollections.observableArrayList();
    private final Map<String, ContactItem> contactMap = new HashMap<>();
    private final Map<Long, String> fileMessagesInView = new HashMap<>();
    private javafx.stage.Stage stage;

    /**
     * Initialise le contrôleur de messagerie après un login réussi.
     * Configure les listes de contacts, le listener des messages serveur, affiche les boutons selon le rôle (organisateur, admin) et demande la liste des contacts.
     * Paramètres : conn – connexion au serveur ; username – nom de l'utilisateur connecté ; role – rôle (MEMBRE, BENEVOLE, ORGANISATEUR).
     * Ne renvoie rien.
     */
    public void init(ServerConnection conn, String username, Role role) {
        this.connection = conn;
        this.username = username;
        this.role = role;

        lbl_user_role.setText(roleLabel(role));
        lbl_status_dot.setStyle("-fx-text-fill: #059669;");
        if (role == Role.ORGANISATEUR) {
            btn_list_members.setVisible(true);
        }
        if ("admin".equals(username)) {
            btn_administration.setVisible(true);
        }

        list_contacts_online.setItems(contactsOnline);
        list_contacts_offline.setItems(contactsOffline);
        list_contacts_online.setCellFactory(createContactCellFactory());
        list_contacts_offline.setCellFactory(createContactCellFactory());

        connection.setMessageListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessage(String line) {
                Platform.runLater(() -> handleServerMessage(line));
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    lbl_status_dot.setStyle("-fx-text-fill: #dc2626;");
                    showError("Perte de connexion: " + reason + " (RG10)");
                });
            }
        });

        refreshContacts();
    }

    /**
     * Définit la fenêtre principale (pour dialogues, notifications et sélecteur de fichiers).
     * Paramètre : s – la fenêtre JavaFX. Ne renvoie rien.
     */
    public void setStage(javafx.stage.Stage s) {
        this.stage = s;
    }

    /**
     * Retourne le libellé affiché pour un rôle (Organisateur, Bénévole, Membre).
     * Paramètre : r – le rôle. Retourne une chaîne en français ; "Membre" si r est null.
     */
    private static String roleLabel(Role r) {
        return r == null ? "Membre" : switch (r) {
            case ORGANISATEUR -> "Organisateur";
            case BENEVOLE -> "Bénévole";
            default -> "Membre";
        };
    }

    /**
     * Crée la fabrique de cellules pour les ListView de contacts (en ligne / hors ligne).
     * Affiche avatar, initiale, nom, badge de rôle, indicateur en ligne/hors ligne et badge de messages non lus.
     * Aucun paramètre. Retourne un Callback pour la création des cellules.
     */
    private Callback<ListView<ContactItem>, ListCell<ContactItem>> createContactCellFactory() {
        return lv -> new ListCell<>() {
            private final HBox box = new HBox(10);
            private final StackPane avatar = new StackPane();
            private final Circle avatarCircle = new Circle(20);
            private final javafx.scene.text.Text initial = new javafx.scene.text.Text();
            private final Circle statusDot = new Circle(5);
            private final VBox nameAndBadge = new VBox(2);
            private final Text nameText = new Text();
            private final Label roleBadge = new Label();
            private final Region spacer = new Region();
            private final Label unreadBadge = new Label();

            {
                box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                avatarCircle.setFill(javafx.scene.paint.Color.web("#7c3aed"));
                initial.setFill(javafx.scene.paint.Color.WHITE);
                initial.setFont(javafx.scene.text.Font.font(14));
                avatar.getChildren().addAll(avatarCircle, initial);
                statusDot.setStroke(javafx.scene.paint.Color.WHITE);
                statusDot.setStrokeWidth(1.5);
                StackPane.setAlignment(statusDot, javafx.geometry.Pos.BOTTOM_RIGHT);
                StackPane.setMargin(statusDot, new javafx.geometry.Insets(0, 2, 2, 0));
                avatar.getChildren().add(statusDot);
                nameAndBadge.getChildren().addAll(nameText, roleBadge);
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                unreadBadge.getStyleClass().add("badge");
                unreadBadge.setVisible(false);
                box.getChildren().addAll(avatar, nameAndBadge, spacer, unreadBadge);
            }

            @Override
            protected void updateItem(ContactItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String name = item.getUsername();
                    nameText.setText(name);
                    initial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
                    statusDot.setFill(item.isOnline() ? Color.web("#059669") : Color.web("#9ca3af"));
                    statusDot.setVisible(true);
                    String r = item.getRoleLabel();
                    roleBadge.setText(r);
                    roleBadge.getStyleClass().removeAll("badge-role-organisateur", "badge-role-membre", "badge-role-benevole");
                    roleBadge.getStyleClass().add(switch (item.getRole() == null ? "" : item.getRole().toUpperCase()) {
                        case "ORGANISATEUR" -> "badge-role-organisateur";
                        case "BENEVOLE" -> "badge-role-benevole";
                        default -> "badge-role-membre";
                    });
                    int unread = item.getUnreadCount();
                    if (unread > 0) {
                        unreadBadge.setText(String.valueOf(unread));
                        unreadBadge.setVisible(true);
                    } else {
                        unreadBadge.setVisible(false);
                    }
                    setGraphic(box);
                }
            }
        };
    }

    /**
     * Appelé quand l'utilisateur sélectionne un contact (en ligne ou hors ligne).
     * Affiche la conversation avec ce contact : vide la zone des messages, met à jour le titre, charge l'historique (GET_HISTORY).
     * Paramètre : event – événement souris (source = list_contacts_online ou list_contacts_offline). Ne renvoie rien.
     */
    @FXML
    void onContactSelected(MouseEvent event) {
        Object src = event.getSource();
        ContactItem item = null;
        if (src == list_contacts_online) item = list_contacts_online.getSelectionModel().getSelectedItem();
        else if (src == list_contacts_offline) item = list_contacts_offline.getSelectionModel().getSelectedItem();
        if (item != null && !item.getUsername().equals(username)) {
            selectedUser = item.getUsername();
            item.clearUnread();
            list_contacts_online.refresh();
            list_contacts_offline.refresh();

            lbl_conversation.setText("Conversation avec " + selectedUser);
            placeholderPane.setVisible(false);
            conversationPane.setVisible(true);
            conversationPane.setManaged(true);
            if (inputArea != null) inputArea.setVisible(true);
            fileMessagesInView.clear();
            messageContainer.getChildren().clear();

            loadHistory(selectedUser);
        }
    }

    /**
     * Envoie le message saisi à l'utilisateur sélectionné (protocole SEND).
     * Vérifie qu'un destinataire est sélectionné, que le message n'est pas vide et fait au plus 1000 caractères (RG7).
     * Ajoute le message dans la vue immédiatement puis vide le champ. En cas d'erreur affiche un message dans lbl_error.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onSendMessage() {
        lbl_error.setText("");
        if (selectedUser == null) return;
        String content = txt_message.getText();
        if (content == null || content.isBlank()) {
            showError("Message vide (RG7)");
            return;
        }
        if (content.length() > 1000) {
            showError("Message trop long (max 1000 caractères)");
            return;
        }

        connection.send(Protocol.SEND, selectedUser, content);
        appendMessage(username, content);
        txt_message.clear();
    }

    /**
     * Actualise l'historique de la conversation courante (bouton Actualiser).
     * Envoie GET_HISTORY pour l'utilisateur sélectionné. Ne fait rien si aucun contact sélectionné.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onRefreshHistory() {
        if (selectedUser != null) loadHistory(selectedUser);
    }

    /**
     * Retourne à l'écran sans conversation sélectionnée (bouton Retour).
     * Réaffiche le placeholder et masque la zone de conversation et le champ de saisie.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onRetourConversation() {
        selectedUser = null;
        list_contacts_online.getSelectionModel().clearSelection();
        list_contacts_offline.getSelectionModel().clearSelection();
        placeholderPane.setVisible(true);
        conversationPane.setVisible(false);
        conversationPane.setManaged(false);
    }

    /**
     * Déconnecte l'utilisateur (LOGOUT) et revient à l'écran de connexion.
     * Ferme la connexion côté client puis charge login-view.fxml.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onLogout() {
        if (connection != null) {
            connection.send(Protocol.LOGOUT);
            connection.disconnect("Déconnexion volontaire");
        }
        retourAuLogin();
    }

    /**
     * Charge l'écran de login et l'affiche dans la même fenêtre.
     * Utilisé après déconnexion. Aucun paramètre. En cas d'IOException appelle Platform.exit().
     */
    private void retourAuLogin() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("login-view.fxml"));
            javafx.scene.Parent root = loader.load();
            LoginController ctrl = loader.getController();
            ctrl.setStage(stage);
            stage.setTitle("Messagerie Interne - Association & Événements");
            stage.setScene(new javafx.scene.Scene(root, 440, 520));
            stage.setResizable(true);
            stage.setMinWidth(320);
            stage.setMinHeight(400);
            stage.show();
        } catch (java.io.IOException e) {
            javafx.application.Platform.exit();
        }
    }

    /**
     * Rafraîchit la liste des contacts (envoie LIST_ALL_CONTACTS au serveur).
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onRefreshContacts() {
        refreshContacts();
    }

    /**
     * Demande la liste des membres (bouton réservé aux organisateurs – RG13).
     * Envoie LIST_MEMBERS. La réponse MEMBERS sera affichée dans la zone de conversation.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onListMembers() {
        connection.send(Protocol.LIST_MEMBERS);
    }

    /**
     * Ouvre l'écran d'administration (réservé à l'utilisateur "admin").
     * Charge admin-view.fxml et enregistre un callback pour revenir à la messagerie à la fermeture.
     * Aucun paramètre. En cas d'erreur affiche un message dans lbl_error.
     */
    @FXML
    void onOpenAdministration() {
        try {
            javafx.scene.Parent messagingRoot = stage.getScene().getRoot();
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("admin-view.fxml"));
            javafx.scene.Parent adminRoot = loader.load();
            AdminController adminCtrl = loader.getController();
            adminCtrl.init(connection, () -> {
                connection.removeMessageListener(adminCtrl);
                stage.getScene().setRoot(messagingRoot);
            });
            stage.getScene().setRoot(adminRoot);
        } catch (Exception e) {
            showError("Impossible d'ouvrir l'administration: " + e.getMessage());
        }
    }

    /**
     * Envoie une requête LIST_ALL_CONTACTS au serveur pour mettre à jour les listes en ligne / hors ligne.
     * Ne fait rien si la connexion est nulle ou déconnectée. Aucun paramètre. Ne renvoie rien.
     */
    void refreshContacts() {
        if (connection != null && connection.isConnected()) {
            connection.send(Protocol.LIST_ALL_CONTACTS);
        }
    }

    /**
     * Demande l'historique de la conversation avec un utilisateur donné (GET_HISTORY).
     * La réponse HISTORY sera traitée dans handleServerMessage.
     * Paramètre : otherUser – nom de l'autre utilisateur. Ne renvoie rien.
     */
    private void loadHistory(String otherUser) {
        connection.send(Protocol.GET_HISTORY, otherUser);
    }

    /**
     * Traite une ligne reçue du serveur (réponses protocole).
     * Gère CONTACTS, LIST_ONLINE, MEMBERS, MESSAGE, FILE_DATA, HISTORY, USER_ONLINE/OFFLINE, ERROR, OK.
     * Paramètre : line – ligne brute reçue. Ne renvoie rien.
     */
    private void handleServerMessage(String line) {
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;

        switch (parts[0]) {
            // Liste de tous les contacts avec statut (en ligne / hors ligne) et rôle.
            // Met à jour les deux ListView et les labels de section.
            case Protocol.CONTACTS -> {
                contactsOnline.clear();
                contactsOffline.clear();
                Map<String, ContactItem> oldMap = new HashMap<>(contactMap);
                contactMap.clear();
                if (parts.length > 1 && !parts[1].isBlank()) {
                    for (String m : parts[1].split(";")) {
                        String[] u = m.split(":");
                        if (u.length >= 2) {
                            String roleStr = u.length >= 3 ? u[2] : null;
                            ContactItem ci = oldMap.get(u[0]);
                            if (ci == null) {
                                ci = new ContactItem(u[0], u[1], roleStr);
                            } else {
                                ci.setStatus(u[1]);
                                if (roleStr != null) ci.setRole(roleStr);
                            }
                            contactMap.put(u[0], ci);
                            if (ci.isOnline()) contactsOnline.add(ci);
                            else contactsOffline.add(ci);
                        }
                    }
                    contactsOnline.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                    contactsOffline.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                }
                lbl_online_count.setText(contactsOnline.size() + " en ligne");
                lbl_section_online.setText("EN LIGNE (" + contactsOnline.size() + ")");
                lbl_section_offline.setText("HORS LIGNE (" + contactsOffline.size() + ")");
                list_contacts_online.refresh();
                list_contacts_offline.refresh();
            }
            // Demande de rafraîchir la liste en ligne : on redemande la liste complète des contacts.
            case Protocol.LIST_ONLINE -> {
                refreshContacts();
            }
            // Réponse à LIST_MEMBERS (organisateur) : affiche la liste des membres dans la zone de conversation.
            case Protocol.MEMBERS -> {
                selectedUser = null;
                fileMessagesInView.clear();
                placeholderPane.setVisible(false);
                conversationPane.setVisible(true);
                conversationPane.setManaged(true);
                if (inputArea != null) inputArea.setVisible(false);
                lbl_conversation.setText("Liste des membres");
                StringBuilder sb = new StringBuilder("Membres inscrits:\n\n");
                if (parts.length > 1 && !parts[1].isBlank()) {
                    for (String m : parts[1].split(";")) {
                        String[] u = m.split(":");
                        if (u.length >= 3) {
                            String status = u.length >= 4 ? " - " + u[3] : "";
                            sb.append("• ").append(u[0]).append(" (").append(u[1]).append(") ").append(u[2]).append(status).append("\n");
                        }
                    }
                }
                appendMessage("Système", sb.toString(), "", "ENVOYE");
            }
            // Nouveau message reçu : si conversation ouverte avec l'expéditeur, on l'affiche ; sinon notification + badge non lu.
            case Protocol.MESSAGE -> {
                if (parts.length > 1) {
                    String[] m = parts[1].split(":", 5);
                    if (m.length >= 4) {
                        String sender  = m[0];
                        String time    = m[1] + ":" + m[2];
                        String content = m[3];
                        String statut  = m.length >= 5 ? m[4] : "ENVOYE";
                        if (selectedUser != null && selectedUser.equals(sender)) {
                            appendMessage(sender, content, time, statut);
                        } else {
                            ContactItem ci = contactMap.get(sender);
                            if (ci != null) {
                                ci.incrementUnread();
                                list_contacts_online.refresh();
                                list_contacts_offline.refresh();
                                String preview = content.startsWith("[FILE]") ? "📎 Fichier" : content;
                                showNotification("Nouveau message de " + sender, preview);
                            }
                        }
                    }
                }
            }
            // Données d'un fichier demandé (REQUEST_FILE) : nom + base64 ; propose l'enregistrement et ouvre le dossier.
            case Protocol.FILE_DATA -> {
                if (parts.length >= 4) {
                    String fileName = parts[2];
                    String base64 = parts[3];
                    saveAndOpenFile(fileName, base64);
                }
            }
            // Historique de la conversation (réponse à GET_HISTORY) : vide la zone puis affiche les messages un par un.
            case Protocol.HISTORY -> {
                int first = line.indexOf('|');
                int second = line.indexOf('|', first + 1);
                String historyForUser = line.substring(first + 1, second);
                String historyStr = line.substring(second + 1);
                if (selectedUser == null || !historyForUser.equals(selectedUser)) return;
                messageContainer.getChildren().clear();
                if (!historyStr.isBlank()) {
                    String[] msgs = historyStr.split("\\|\\|");
                    for (String msg : msgs) {
                        if (msg.isBlank()) continue;
                        String[] m = msg.split(":", 4);
                        if (m.length >= 4) {
                            String time    = m[1] + ":" + m[2];
                            String content = m[3].replace("::", ":").replace("|||", "||");
                            String statut  = "ENVOYE";
                            appendMessage(m[0], content, time, statut);
                        }
                    }
                }
            }
            // Un utilisateur s'est connecté ou déconnecté : on rafraîchit la liste des contacts pour mettre à jour le statut.
            case Protocol.USER_ONLINE, Protocol.USER_OFFLINE -> {
                refreshContacts();
            }
            // Erreur renvoyée par le serveur : affichage du message dans le label d'erreur.
            case Protocol.ERROR -> {
                if (parts.length > 1) showError(parts[1]);
            }
            // Accusé de réception (OK|RECU) : met à jour l'indicateur ✓✓ sur le dernier message envoyé.
            case Protocol.OK -> {
                if (parts.length >= 2 && "RECU".equals(parts[1]) && lastCheckLabel != null) {
                    lastCheckLabel.setText("✓✓");
                    lastCheckLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
                }
            }
            // Commande inconnue ou sans traitement côté client : on ignore.
            default -> {}
        }
    }

    /**
     * Affiche une notification type popup (nouveau message) et met la fenêtre au premier plan.
     * Paramètres : title – titre (ex. "Nouveau message de user1") ; preview – aperçu du message (tronqué à 50 caractères).
     * Ne renvoie rien.
     */
    private void showNotification(String title, String preview) {
        if (stage != null) {
            stage.setTitle("● " + title + " - Messagerie");
            stage.toFront();
            stage.requestFocus();
            com.example.exam_java.util.NotificationUtil.show(stage, title, preview.length() > 50 ? preview.substring(0, 50) + "..." : preview);
        }
    }

    /**
     * Ajoute une bulle de message dans la zone de conversation avec l'heure courante (pour un envoi local).
     * Paramètres : sender – expéditeur ; content – contenu. Délègue à appendMessage(sender, content, time, "ENVOYE"). Ne renvoie rien.
     */
    private void appendMessage(String sender, String content) {
        String time = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        appendMessage(sender, content, time, "ENVOYE");
    }

    /**
     * Ajoute une bulle de message avec heure fournie (surcharge sans statut).
     * Paramètres : sender – expéditeur ; content – contenu ; time – heure à afficher. Ne renvoie rien.
     */
    private void appendMessage(String sender, String content, String time) {
        appendMessage(sender, content, time, "ENVOYE");
    }

    /**
     * Ajoute une bulle de message dans messageContainer (alignement gauche/droite selon expéditeur).
     * Gère l'affichage des fichiers [FILE] avec ID et nom, et les indicateurs ✓ / ✓✓ selon le statut (ENVOYE, RECU, LU).
     * Fait défiler la zone vers le bas. Paramètres : sender, content, time, statut. Ne renvoie rien.
     */
    private void appendMessage(String sender, String content, String time, String statut) {
        boolean isMine = sender.equals(username);
        String displayContent = content;
        if (content.startsWith("[FILE]")) {
            int pipe = content.indexOf("|");
            if (pipe > 6) {
                try {
                    long msgId = Long.parseLong(content.substring(6, pipe));
                    String fileName = content.substring(pipe + 1);
                    fileMessagesInView.put(msgId, fileName);
                    displayContent = "📎 Fichier: " + fileName + " [ID:" + msgId + "]";
                } catch (Exception e) {
                    displayContent = "📎 " + content;
                }
            }
        }
        Label bubble = new Label(displayContent);
        bubble.setWrapText(true);
        bubble.setMaxWidth(400);
        bubble.setStyle(isMine
                ? "-fx-background-color: #7c3aed; -fx-background-radius: 16 4 16 16;" +
                "-fx-text-fill: white; -fx-font-size: 13px; -fx-padding: 10 14 10 14;"
                : "-fx-background-color: #1e2535; -fx-background-radius: 4 16 16 16;" +
                "-fx-text-fill: #e0e4f0; -fx-font-size: 13px; -fx-padding: 10 14 10 14;"
        );
        Label timeLabel = new Label(time);
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
        VBox bubbleBox = new VBox(3);
        if (!isMine) {
            Label senderLabel = new Label(sender);
            senderLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #7c3aed; -fx-font-weight: bold;");
            bubbleBox.getChildren().add(senderLabel);
        }
        bubbleBox.getChildren().add(bubble);
        if (isMine) {
            Label checkLabel = new Label("✓");
            checkLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;");
            // Mettre à jour selon le statut reçu
            switch (statut) {
                case "RECU" -> { checkLabel.setText("✓✓"); checkLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;"); }
                case "LU"   -> { checkLabel.setText("✓✓"); checkLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #27ae60;"); }
                default     -> { checkLabel.setText("✓");  checkLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9ca3af;"); }
            }
            lastCheckLabel = checkLabel; // garde référence
            HBox timeRow = new HBox(4, timeLabel, checkLabel);
            timeRow.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            bubbleBox.getChildren().add(timeRow);
        } else {
            bubbleBox.getChildren().add(timeLabel);
        }
        bubbleBox.setMaxWidth(420);
        bubbleBox.setAlignment(isMine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        HBox row = new HBox();
        row.setAlignment(isMine ? javafx.geometry.Pos.CENTER_RIGHT : javafx.geometry.Pos.CENTER_LEFT);
        row.getChildren().add(bubbleBox);
        messageContainer.getChildren().add(row);
        scrollPane.layout();
        scrollPane.setVvalue(1.0);
    }

    /**
     * Ouvre un sélecteur de fichier et envoie le fichier choisi au destinataire sélectionné (SEND_FILE).
     * Limite 5 Mo. En cas d'erreur affiche un message dans lbl_error.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onSendFile() {
        if (selectedUser == null) {
            showError("Sélectionnez un destinataire");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Choisir un fichier à envoyer");
        File file = fc.showOpenDialog(stage);
        if (file != null && file.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                if (bytes.length > 5 * 1024 * 1024) {
                    showError("Fichier trop volumineux (max 5 Mo)");
                    return;
                }
                String base64 = Base64.getEncoder().encodeToString(bytes);
                connection.send(Protocol.SEND_FILE, selectedUser, file.getName(), base64);
                appendMessage(username, "[FICHIER] " + file.getName());
                showError("");
            } catch (Exception e) {
                showError("Erreur: " + e.getMessage());
            }
        }
    }

    /**
     * Permet de télécharger un fichier affiché dans la conversation (REQUEST_FILE).
     * Si plusieurs fichiers sont présents, affiche une boîte de choix. Sinon envoie directement la requête.
     * Aucun paramètre. Ne renvoie rien. Affiche une erreur si aucun fichier dans la conversation.
     */
    @FXML
    void onDownloadFile() {
        if (fileMessagesInView.isEmpty()) {
            showError("Aucun fichier à télécharger dans cette conversation");
            return;
        }
        Long msgId;
        if (fileMessagesInView.size() == 1) {
            msgId = fileMessagesInView.keySet().iterator().next();
        } else {
            ChoiceDialog<String> dialog = new ChoiceDialog<>();
            dialog.setTitle("Télécharger un fichier");
            dialog.setHeaderText("Choisir le fichier à télécharger");
            var fileLabels = fileMessagesInView.entrySet().stream()
                    .map(e -> e.getValue() + " (ID:" + e.getKey() + ")")
                    .toList();
            dialog.getItems().addAll(fileLabels);
            dialog.setSelectedItem(fileLabels.get(0));
            var result = dialog.showAndWait();
            if (result.isEmpty()) return;
            String selected = result.get();
            int idStart = selected.lastIndexOf("(ID:") + 4;
            msgId = Long.parseLong(selected.substring(idStart, selected.indexOf(")", idStart)));
        }
        connection.send(Protocol.REQUEST_FILE, String.valueOf(msgId));
    }

    /**
     * Décode le contenu base64, propose d'enregistrer le fichier via FileChooser et ouvre le dossier parent si possible.
     * Paramètres : fileName – nom suggéré ; base64 – contenu encodé. Ne renvoie rien. En cas d'erreur affiche un message.
     */
    private void saveAndOpenFile(String fileName, String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le fichier");
            fc.setInitialFileName(fileName);
            File file = fc.showSaveDialog(stage);
            if (file != null) {
                Files.write(file.toPath(), bytes);
                lbl_error.setStyle("-fx-text-fill: #27ae60;");
                lbl_error.setText("Fichier enregistré: " + file.getAbsolutePath());
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(file.getParentFile());
                }
            }
        } catch (Exception e) {
            showError("Erreur sauvegarde: " + e.getMessage());
        }
    }

    /**
     * Affiche un message d'erreur dans le label prévu (style rouge).
     * Paramètre : msg – texte à afficher. Ne renvoie rien.
     */
    private void showError(String msg) {
        lbl_error.setText(msg);
        lbl_error.setStyle("-fx-text-fill: #e74c3c;");
    }
}