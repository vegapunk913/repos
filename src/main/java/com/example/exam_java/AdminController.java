package com.example.exam_java;

import com.example.exam_java.entity.Role;
import com.example.exam_java.model.AdminUserRow;
import com.example.exam_java.network.ServerConnection;
import com.example.exam_java.protocol.Protocol;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.Optional;

public class AdminController implements ServerConnection.MessageListener {

    @FXML private ListView<String> list_pending;
    @FXML private Label lbl_validation_section;
    @FXML private Label lbl_validation_status;
    @FXML private TableView<AdminUserRow> table_users;
    @FXML private TableColumn<AdminUserRow, String> col_username;
    @FXML private TableColumn<AdminUserRow, String> col_role;
    @FXML private TableColumn<AdminUserRow, String> col_compte;
    @FXML private TableColumn<AdminUserRow, String> col_status;
    @FXML private TextField txt_search;
    @FXML private Label lbl_gestion_status;
    @FXML private Button btn_block;
    @FXML private Button btn_unblock;

    private ServerConnection connection;
    private Runnable onClose;
    private String selectedPendingUser;
    private final ObservableList<AdminUserRow> usersList = FXCollections.observableArrayList();
    private FilteredList<AdminUserRow> usersFiltered;
    private boolean listRequestRetried;

    /**
     * Initialise l'écran d'administration : enregistre le listener, configure le tableau, demande LIST_PENDING et ADMIN_LIST_USERS.
     * Paramètres : conn – connexion serveur ; onClose – callback appelé à la fermeture (retour messagerie). Ne renvoie rien.
     */
    public void init(ServerConnection conn, Runnable onClose) {
        this.connection = conn;
        this.onClose = onClose;
        connection.addMessageListener(this);
        setupTable();
        updateBlockUnblockButtons(null);
        connection.send(Protocol.LIST_PENDING);
        connection.send(Protocol.ADMIN_LIST_USERS);
    }

    /**
     * Reçoit les messages du serveur (interface MessageListener). Délègue à handleMessage sur le thread JavaFX.
     * Paramètre : line – ligne reçue. Ne renvoie rien.
     */
    @Override
    public void onMessage(String line) {
        Platform.runLater(() -> handleMessage(line));
    }

    /**
     * Appelé en cas de perte de connexion. Affiche "Déconnecté: ..." dans les labels de statut.
     * Paramètre : reason – raison de la déconnexion. Ne renvoie rien.
     */
    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> {
            if (lbl_gestion_status != null) lbl_gestion_status.setText("Déconnecté: " + reason);
            if (lbl_validation_status != null) lbl_validation_status.setText("Déconnecté: " + reason);
        });
    }

    /**
     * Traite les réponses serveur : PENDING_USERS, VALIDATE_SUCCESS/FAIL, USERS_LIST, USER_CREATED/BLOCKED/UNBLOCKED/DELETED, ERROR.
     * Met à jour les listes et les labels de statut. Paramètre : line – ligne reçue. Ne renvoie rien.
     */
    private void handleMessage(String line) {
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;
        switch (parts[0]) {
            case Protocol.PENDING_USERS -> {
                ObservableList<String> pending = FXCollections.observableArrayList();
                if (parts.length > 1 && !parts[1].isBlank()) {
                    for (String m : parts[1].split(";")) {
                        String[] u = m.split(":");
                        if (u.length >= 2) pending.add(u[0] + " (" + u[1] + ")");
                    }
                }
                list_pending.setItems(pending);
                if (lbl_validation_section != null) lbl_validation_section.setText("VALIDATION (" + pending.size() + ")");
                setValidationStatus("", true);
            }
            case Protocol.VALIDATE_SUCCESS -> {
                setValidationStatus(parts.length > 1 ? "Utilisateur " + parts[1] + " validé." : "Validé.", true);
                connection.send(Protocol.LIST_PENDING);
            }
            case Protocol.VALIDATE_FAIL -> {
                setValidationStatus(parts.length > 1 ? parts[1] : "Échec validation", false);
            }
            case Protocol.USERS_LIST -> {
                usersList.clear();
                if (parts.length > 1 && !parts[1].isBlank()) {
                    for (String m : parts[1].split(";")) {
                        String[] u = m.split(":");
                        if (u.length >= 5) {
                            boolean validated = Boolean.parseBoolean(u[2]);
                            boolean blocked = Boolean.parseBoolean(u[3]);
                            boolean online = Boolean.parseBoolean(u[4]);
                            String roleDisplay = u[1];
                            try {
                                if (u[1] != null && !u[1].isEmpty()) roleDisplay = roleLabel(Role.valueOf(u[1]));
                            } catch (Exception ignored) {}
                            usersList.add(new AdminUserRow(u[0], roleDisplay, validated, blocked, online));
                        }
                    }
                }
                setGestionStatus("Liste actualisée.", true);
                listRequestRetried = false;
                Platform.runLater(() -> updateBlockUnblockButtons(table_users.getSelectionModel().getSelectedItem()));
            }
            case Protocol.USER_CREATED -> {
                setGestionStatus(parts.length > 1 ? "Utilisateur " + parts[1] + " créé." : "Créé.", true);
                connection.send(Protocol.ADMIN_LIST_USERS);
            }
            case Protocol.USER_BLOCKED -> {
                setGestionStatus(parts.length > 1 ? "Utilisateur " + parts[1] + " bloqué." : "Bloqué.", true);
                connection.send(Protocol.ADMIN_LIST_USERS);
            }
            case Protocol.USER_UNBLOCKED -> {
                setGestionStatus(parts.length > 1 ? "Utilisateur " + parts[1] + " débloqué." : "Débloqué.", true);
                connection.send(Protocol.ADMIN_LIST_USERS);
            }
            case Protocol.USER_DELETED -> {
                setGestionStatus(parts.length > 1 ? "Utilisateur " + parts[1] + " supprimé." : "Supprimé.", true);
                connection.send(Protocol.ADMIN_LIST_USERS);
            }
            case Protocol.ERROR -> {
                if (parts.length > 1) {
                    String msg = parts[1];
                    setValidationStatus(msg, false);
                    setGestionStatus(msg, false);
                    if (!listRequestRetried && connection != null && connection.isConnected()) {
                        listRequestRetried = true;
                        connection.send(Protocol.ADMIN_LIST_USERS);
                    }
                }
            }
            default -> {}
        }
    }

    /**
     * Retourne le libellé affiché pour un rôle (Organisateur, Bénévole, Membre).
     * Paramètre : r – le rôle. Retourne une chaîne en français.
     */
    private static String roleLabel(Role r) {
        return r == null ? "Membre" : switch (r) {
            case ORGANISATEUR -> "Organisateur";
            case BENEVOLE -> "Bénévole";
            default -> "Membre";
        };
    }

    /**
     * Configure le TableView des utilisateurs : FilteredList, colonnes, placeholder, listener de sélection pour Bloquer/Débloquer.
     * Aucun paramètre. Ne renvoie rien.
     */
    private void setupTable() {
        usersFiltered = new FilteredList<>(usersList, p -> true);
        table_users.setItems(usersFiltered);
        Label placeholder = new Label("Aucun contenu dans la table");
        placeholder.getStyleClass().add("placeholder-text");
        table_users.setPlaceholder(placeholder);
        col_username.setCellValueFactory(c -> c.getValue().usernameProperty());
        col_role.setCellValueFactory(c -> c.getValue().roleProperty());
        col_compte.setCellValueFactory(c -> c.getValue().compteActifProperty());
        col_status.setCellValueFactory(c -> c.getValue().statusProperty());
        table_users.getSelectionModel().selectedItemProperty().addListener((o, oldVal, newVal) -> updateBlockUnblockButtons(newVal));
    }

    /**
     * Active/désactive les boutons Bloquer et Débloquer selon l'utilisateur sélectionné (admin non bloquable).
     * Paramètre : selected – ligne sélectionnée ou null. Ne renvoie rien.
     */
    private void updateBlockUnblockButtons(AdminUserRow selected) {
        if (btn_block == null || btn_unblock == null) return;
        if (selected == null || "admin".equals(selected.getUsername())) {
            btn_block.setDisable(true);
            btn_unblock.setDisable(true);
        } else {
            btn_block.setDisable(selected.isBlocked());
            btn_unblock.setDisable(!selected.isBlocked());
        }
    }

    /**
     * Appelé lors d'un clic sur le tableau utilisateurs. Met à jour l'état des boutons Bloquer/Débloquer.
     * Paramètre : event – événement souris. Ne renvoie rien.
     */
    @FXML
    void onTableUserSelected(MouseEvent event) {
        updateBlockUnblockButtons(table_users.getSelectionModel().getSelectedItem());
    }

    /**
     * Affiche un message dans le label de statut de la section validation (couleur selon succès/échec).
     * Paramètres : msg – texte ; success – true = gris, false = rouge. Ne renvoie rien.
     */
    private void setValidationStatus(String msg, boolean success) {
        if (lbl_validation_status == null) return;
        lbl_validation_status.setText(msg);
        lbl_validation_status.setStyle(success ? "-fx-text-fill: #6b7280;" : "-fx-text-fill: #dc2626;");
    }

    /**
     * Affiche un message dans le label de statut de la section gestion des utilisateurs.
     * Paramètres : msg – texte ; success – true = gris, false = rouge. Ne renvoie rien.
     */
    private void setGestionStatus(String msg, boolean success) {
        if (lbl_gestion_status == null) return;
        lbl_gestion_status.setText(msg);
        lbl_gestion_status.setStyle(success ? "-fx-text-fill: #6b7280;" : "-fx-text-fill: #dc2626;");
    }

    /**
     * Ferme l'écran d'administration et exécute le callback onClose (retour à la messagerie).
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onClose() {
        if (onClose != null) onClose.run();
    }

    /**
     * Mémorise l'utilisateur en attente sélectionné dans la liste (pour validation).
     * Extrait le username du libellé "username (rôle)". Paramètre : event – clic. Ne renvoie rien.
     */
    @FXML
    void onPendingSelected(MouseEvent event) {
        String item = list_pending.getSelectionModel().getSelectedItem();
        if (item != null) selectedPendingUser = item.split(" ")[0];
    }

    /**
     * Rafraîchit la liste des utilisateurs en attente de validation (LIST_PENDING).
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onRefreshPending() {
        connection.send(Protocol.LIST_PENDING);
        setValidationStatus("", true);
    }

    /**
     * Valide l'utilisateur sélectionné dans la liste "en attente" (VALIDATE). Réservé à l'admin.
     * Affiche une erreur si aucun utilisateur sélectionné. Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onValidate() {
        if (selectedPendingUser == null) {
            setValidationStatus("Sélectionnez un utilisateur à valider.", false);
            return;
        }
        connection.send(Protocol.VALIDATE, selectedPendingUser);
        setValidationStatus("Validation en cours...", true);
    }

    /**
     * Filtre le tableau des utilisateurs selon le texte saisi dans txt_search (username ou rôle).
     * Paramètre : event – frappe clavier. Ne renvoie rien.
     */
    @FXML
    void onSearchKey(KeyEvent event) {
        if (usersFiltered == null) return;
        String q = txt_search.getText() != null ? txt_search.getText().trim().toLowerCase() : "";
        usersFiltered.setPredicate(row -> q.isEmpty()
                || row.getUsername().toLowerCase().contains(q)
                || row.getRole().toLowerCase().contains(q));
    }

    /**
     * Ouvre des dialogues pour créer un utilisateur : nom, mot de passe, rôle puis envoie ADMIN_CREATE_USER.
     * Réservé à l'admin. Si l'utilisateur annule une étape, la création est abandonnée.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onCreateUser() {
        TextInputDialog d1 = new TextInputDialog();
        d1.setTitle("Créer un utilisateur");
        d1.setHeaderText("Nom d'utilisateur");
        d1.setContentText("Nom d'utilisateur:");
        Optional<String> uOpt = d1.showAndWait();
        if (uOpt.isEmpty() || uOpt.get().isBlank()) return;
        String un = uOpt.get().trim();

        TextInputDialog d2 = new TextInputDialog();
        d2.setTitle("Mot de passe");
        d2.setHeaderText("Mot de passe pour " + un);
        d2.setContentText("Mot de passe:");
        Optional<String> pOpt = d2.showAndWait();
        if (pOpt.isEmpty()) return;

        ChoiceDialog<String> d3 = new ChoiceDialog<>("MEMBRE", "MEMBRE", "BENEVOLE", "ORGANISATEUR");
        d3.setTitle("Rôle");
        d3.setHeaderText("Rôle pour " + un);
        d3.setContentText("Rôle:");
        Optional<String> rOpt = d3.showAndWait();
        if (rOpt.isEmpty()) return;

        connection.send(Protocol.ADMIN_CREATE_USER, un, pOpt.get(), rOpt.get());
        setGestionStatus("Création en cours...", true);
    }

    /**
     * Bloque l'utilisateur sélectionné dans le tableau (ADMIN_BLOCK_USER). Confirmation demandée. Admin non bloquable.
     * Affiche un message d'erreur si aucune sélection ou si admin. Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onBlockUser() {
        AdminUserRow row = table_users.getSelectionModel().getSelectedItem();
        if (row == null) {
            setGestionStatus("Sélectionnez un utilisateur.", false);
            return;
        }
        if ("admin".equals(row.getUsername())) {
            setGestionStatus("Impossible de bloquer l'administrateur.", false);
            return;
        }
        if (new Alert(Alert.AlertType.CONFIRMATION, "Bloquer " + row.getUsername() + " ?", ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            connection.send(Protocol.ADMIN_BLOCK_USER, row.getUsername());
            setGestionStatus("Blocage en cours...", true);
        }
    }

    /**
     * Débloque l'utilisateur sélectionné (ADMIN_UNBLOCK_USER). Affiche une erreur si aucune sélection.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onUnblockUser() {
        AdminUserRow row = table_users.getSelectionModel().getSelectedItem();
        if (row == null) {
            setGestionStatus("Sélectionnez un utilisateur.", false);
            return;
        }
        connection.send(Protocol.ADMIN_UNBLOCK_USER, row.getUsername());
        setGestionStatus("Déblocage en cours...", true);
    }

    /**
     * Supprime définitivement l'utilisateur sélectionné (ADMIN_DELETE_USER). Confirmation demandée. Admin non supprimable.
     * Affiche une erreur si aucune sélection ou si admin. Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onDeleteUser() {
        AdminUserRow row = table_users.getSelectionModel().getSelectedItem();
        if (row == null) {
            setGestionStatus("Sélectionnez un utilisateur.", false);
            return;
        }
        if ("admin".equals(row.getUsername())) {
            setGestionStatus("Impossible de supprimer l'administrateur.", false);
            return;
        }
        if (new Alert(Alert.AlertType.CONFIRMATION, "Supprimer définitivement " + row.getUsername() + " ? Cette action est irréversible.", ButtonType.OK, ButtonType.CANCEL).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            connection.send(Protocol.ADMIN_DELETE_USER, row.getUsername());
            setGestionStatus("Suppression en cours...", true);
        }
    }
}
