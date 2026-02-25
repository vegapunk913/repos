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

public class MessagingController {

    @FXML private Label lbl_user_role;
    @FXML private Label lbl_status_dot;
    @FXML private Label lbl_online_count;
    @FXML private Label lbl_section_online;
    @FXML private Label lbl_section_offline;
    @FXML private Label lbl_conversation;
    @FXML private Label lbl_error;
    @FXML private ListView<ContactItem> list_contacts_online;
    @FXML private ListView<ContactItem> list_contacts_offline;
    @FXML private TextArea area_messages;
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

    private final ObservableList<ContactItem> contactsOnline = FXCollections.observableArrayList();
    private final ObservableList<ContactItem> contactsOffline = FXCollections.observableArrayList();
    private final Map<String, ContactItem> contactMap = new HashMap<>();
    private final Map<Long, String> fileMessagesInView = new HashMap<>();
    private javafx.stage.Stage stage;

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

    public void setStage(javafx.stage.Stage s) {
        this.stage = s;
    }

    private static String roleLabel(Role r) {
        return r == null ? "Membre" : switch (r) {
            case ORGANISATEUR -> "Organisateur";
            case BENEVOLE -> "Bénévole";
            default -> "Membre";
        };
    }

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

            loadHistory(selectedUser);
        }
    }

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

    @FXML
    void onRefreshHistory() {
        if (selectedUser != null) loadHistory(selectedUser);
    }

    @FXML
    void onRetourConversation() {
        selectedUser = null;
        list_contacts_online.getSelectionModel().clearSelection();
        list_contacts_offline.getSelectionModel().clearSelection();
        placeholderPane.setVisible(true);
        conversationPane.setVisible(false);
        conversationPane.setManaged(false);
    }

    @FXML
    void onLogout() {
        if (connection != null) {
            connection.send(Protocol.LOGOUT);
            connection.disconnect("Déconnexion volontaire");
        }
        retourAuLogin();
    }

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

    @FXML
    void onRefreshContacts() {
        refreshContacts();
    }

    @FXML
    void onListMembers() {
        connection.send(Protocol.LIST_MEMBERS);
    }

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

    void refreshContacts() {
        if (connection != null && connection.isConnected()) {
            connection.send(Protocol.LIST_ALL_CONTACTS);
        }
    }

    private void loadHistory(String otherUser) {
        connection.send(Protocol.GET_HISTORY, otherUser);
    }

    private void handleServerMessage(String line) {
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;

        switch (parts[0]) {
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
            case Protocol.LIST_ONLINE -> {
                refreshContacts();
            }
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
                area_messages.setText(sb.toString());
            }
            case Protocol.MESSAGE -> {
                if (parts.length > 1) {
                    String[] m = parts[1].split(":", 3);
                    if (m.length >= 3) {
                        String sender = m[0];
                        String time = m[1];
                        String content = m[2];

                        if (selectedUser != null && selectedUser.equals(sender)) {
                            appendMessage(sender, content, time);
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
            case Protocol.FILE_DATA -> {
                if (parts.length >= 4) {
                    String fileName = parts[2];
                    String base64 = parts[3];
                    saveAndOpenFile(fileName, base64);
                }
            }
            case Protocol.HISTORY -> {
                if (parts.length >= 3) {
                    String historyForUser = parts[1];
                    if (selectedUser == null || !historyForUser.equals(selectedUser)) return;
                    area_messages.clear();
                    String historyStr = parts[2];
                    if (!historyStr.isBlank()) {
                        String[] msgs = historyStr.split("\\|\\|");
                        for (String msg : msgs) {
                            String[] m = msg.split(":", 3);
                            if (m.length >= 3) {
                                String content = m[2].replace("::", ":").replace("|||", "||");
                                appendMessage(m[0], content, m[1]);
                            }
                        }
                    }
                }
            }
            case Protocol.USER_ONLINE, Protocol.USER_OFFLINE -> {
                refreshContacts();
            }
            case Protocol.ERROR -> {
                if (parts.length > 1) showError(parts[1]);
            }
            default -> {}
        }
    }

    private void showNotification(String title, String preview) {
        if (stage != null) {
            stage.setTitle("● " + title + " - Messagerie");
            stage.toFront();
            stage.requestFocus();
            com.example.exam_java.util.NotificationUtil.show(stage, title, preview.length() > 50 ? preview.substring(0, 50) + "..." : preview);
        }
    }

    private void appendMessage(String sender, String content) {
        appendMessage(sender, content, java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
    }

    private void appendMessage(String sender, String content, String time) {
        String prefix = sender.equals(username) ? "Vous" : sender;
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
        area_messages.appendText("[" + time + "] " + prefix + ": " + displayContent + "\n");
    }

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

    private void showError(String msg) {
        lbl_error.setText(msg);
        lbl_error.setStyle("-fx-text-fill: #e74c3c;");
    }
}
