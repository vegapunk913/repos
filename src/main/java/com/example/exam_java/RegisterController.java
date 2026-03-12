package com.example.exam_java;

import com.example.exam_java.network.ServerConnection;
import com.example.exam_java.protocol.Protocol;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class RegisterController {

    @FXML private TextField txt_username;
    @FXML private PasswordField txt_password;
    @FXML private PasswordField txt_password_confirm;
    @FXML private ComboBox<String> cbx_role;
    @FXML private Label lbl_status;

    private Stage stage;
    private String host = "localhost";
    private String port = "9999";

    /**
     * Initialise la vue d'inscription : remplit la liste des rôles (MEMBRE, BENEVOLE, ORGANISATEUR).
     * Appelé automatiquement par JavaFX après chargement du FXML. Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    public void initialize() {
        cbx_role.getItems().addAll("MEMBRE", "BENEVOLE", "ORGANISATEUR");
        cbx_role.getSelectionModel().selectFirst();
    }

    /**
     * Définit la fenêtre principale pour la navigation.
     * Paramètre : stage – la fenêtre JavaFX. Ne renvoie rien.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /**
     * Mémorise l'hôte et le port pour la connexion au serveur (transmis depuis l'écran de login).
     * Paramètres : host – adresse du serveur (null = "localhost") ; port – port (null = "9999"). Ne renvoie rien.
     */
    public void setHostPort(String host, String port) {
        this.host = host != null ? host : "localhost";
        this.port = port != null ? port : "9999";
    }

    /**
     * Applique une classe CSS au label de statut (loading, error, success).
     * Paramètre : styleClass – nom de la classe, ou null pour tout retirer. Ne renvoie rien.
     */
    private void setStatusStyle(String styleClass) {
        lbl_status.getStyleClass().removeAll("login-status-loading", "login-status-error", "login-status-success");
        if (styleClass != null) lbl_status.getStyleClass().add(styleClass);
    }

    /**
     * Retourne à l'écran de connexion (login). Charge login-view.fxml et remplace la racine de la scène.
     * Aucun paramètre. En cas d'IOException affiche l'erreur dans lbl_status.
     */
    @FXML
    void onGoLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Parent root = loader.load();
            LoginController ctrl = loader.getController();
            ctrl.setStage(stage);
            if (stage != null && stage.getScene() != null) {
                stage.getScene().setRoot(root);
            }
        } catch (IOException e) {
            lbl_status.setText("Erreur: " + e.getMessage());
        }
    }

    /**
     * Envoie une demande d'inscription au serveur (REGISTER) avec username, password et rôle.
     * Vérifie que les champs ne sont pas vides et que les deux mots de passe correspondent.
     * En cas d'échec de connexion TCP ou de réponse REGISTER_FAIL, affiche un message dans lbl_status.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onRegister() {
        lbl_status.setText("");
        setStatusStyle(null);
        String username = txt_username.getText();
        String password = txt_password.getText();
        String confirm = txt_password_confirm.getText();
        String role = cbx_role.getSelectionModel().getSelectedItem();

        if (username.isBlank() || password.isBlank()) {
            setStatusStyle("login-status-error");
            lbl_status.setText("Veuillez remplir tous les champs");
            return;
        }
        if (!password.equals(confirm)) {
            setStatusStyle("login-status-error");
            lbl_status.setText("Les mots de passe ne correspondent pas");
            return;
        }

        ServerConnection connection = new ServerConnection();
        connection.setMessageListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessage(String line) {
                Platform.runLater(() -> handleRegisterResponse(line));
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    setStatusStyle("login-status-error");
                    lbl_status.setText("Déconnecté: " + reason);
                });
            }
        });

        if (!connection.connect(host, parsePort(port))) {
            setStatusStyle("login-status-error");
            lbl_status.setText("Impossible de se connecter. Démarrez le serveur.");
            return;
        }

        setStatusStyle("login-status-loading");
        lbl_status.setText("Inscription en cours...");
        connection.send(Protocol.REGISTER, username, password, role);
    }

    /**
     * Traite la réponse du serveur après une tentative d'inscription.
     * REGISTER_SUCCESS : affiche un message de succès ; REGISTER_FAIL : affiche le message d'erreur.
     * Paramètre : line – ligne reçue du serveur. Ne renvoie rien.
     */
    private void handleRegisterResponse(String line) {
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case Protocol.REGISTER_SUCCESS -> {
                setStatusStyle("login-status-success");
                lbl_status.setText("Inscription réussie ! En attente de validation par l'administrateur.");
            }
            case Protocol.REGISTER_FAIL -> {
                setStatusStyle("login-status-error");
                lbl_status.setText(parts.length > 1 ? parts[1] : "Échec d'inscription");
            }
            default -> {}
        }
    }

    /**
     * Convertit le texte du port en entier.
     * Paramètre : text – chaîne (ex. "9999"). Retourne le numéro de port, ou 9999 si invalide.
     */
    private int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 9999;
        }
    }
}
