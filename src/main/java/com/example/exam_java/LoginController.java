package com.example.exam_java;

import com.example.exam_java.entity.Role;
import com.example.exam_java.network.ServerConnection;
import com.example.exam_java.protocol.Protocol;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private TextField txt_host;
    @FXML private TextField txt_port;
    @FXML private TextField txt_username;
    @FXML private PasswordField txt_password;
    @FXML private Label lbl_status;

    private ServerConnection connection;
    private Stage stage;

    /**
     * Ouvre l'écran d'inscription (register).
     * Récupère l'hôte et le port saisis et les transmet au RegisterController.
     * Ne prend aucun paramètre. En cas d'erreur de chargement FXML, affiche le message dans lbl_status.
     */
    @FXML
    void onGoRegister() {
        try {
            Stage s = stage != null ? stage : (javafx.stage.Stage) txt_username.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register-view.fxml"));
            Parent root = loader.load();
            RegisterController ctrl = loader.getController();
            ctrl.setStage(s);
            ctrl.setHostPort(txt_host.getText(), txt_port.getText());
            s.getScene().setRoot(root);
        } catch (IOException e) {
            lbl_status.setText("Erreur: " + e.getMessage());
        }
    }

    /**
     * Enregistre la connexion serveur utilisée pour le login.
     * Paramètre : connection – instance de ServerConnection. Ne renvoie rien.
     */
    public void setConnection(ServerConnection connection) {
        this.connection = connection;
    }

    /**
     * Définit la fenêtre principale (pour navigation vers messagerie ou register).
     * Paramètre : stage – la fenêtre JavaFX. Ne renvoie rien.
     */
    public void setStage(Stage stage) {
        this.stage = stage;
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
     * Tente la connexion au serveur avec les identifiants saisis.
     * Lit host, port, username, password ; vérifie que username et password ne sont pas vides ;
     * établit la connexion et envoie LOGIN au serveur. En cas d'échec de connexion TCP, affiche un message d'erreur.
     * Aucun paramètre. Ne renvoie rien.
     */
    @FXML
    void onLogin() {
        lbl_status.setText("");
        setStatusStyle(null);
        String host = txt_host.getText();
        int port = parsePort(txt_port.getText());
        String username = txt_username.getText();
        String password = txt_password.getText();

        if (username.isBlank() || password.isBlank()) {
            lbl_status.setText("Veuillez remplir tous les champs");
            return;
        }

        connection = new ServerConnection();
        connection.setMessageListener(new ServerConnection.MessageListener() {
            @Override
            public void onMessage(String line) {
                Platform.runLater(() -> handleResponse(line, username));
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    setStatusStyle("login-status-error");
                    lbl_status.setText("Déconnecté: " + reason);
                });
            }
        });

        if (!connection.connect(host, port)) {
            setStatusStyle("login-status-error");
            String msg = "Impossible de se connecter à " + host + ":" + port + ". Démarrez le serveur et Docker.";
            lbl_status.setText(msg);
            return;
        }

        setStatusStyle("login-status-loading");
        lbl_status.setText("Connexion en cours...");
        connection.send(Protocol.LOGIN, username, password);
    }

    /**
     * Traite la réponse du serveur après une tentative de login.
     * Si LOGIN_SUCCESS : ouvre la vue messagerie avec le rôle reçu. Si LOGIN_FAIL : affiche le message d'erreur.
     * Paramètres : line – ligne reçue du serveur ; expectedUsername – login envoyé. Ne renvoie rien.
     */
    private void handleResponse(String line, String expectedUsername) {
        String[] parts = Protocol.parse(line);
        if (parts.length == 0) return;

        switch (parts[0]) {
            case Protocol.LOGIN_SUCCESS -> openMessagingView(connection, expectedUsername, parts.length > 2 ? parts[2] : "MEMBRE");
            case Protocol.LOGIN_FAIL -> {
                setStatusStyle("login-status-error");
                lbl_status.setText(parts.length > 1 ? parts[1] : "Échec de connexion");
            }
            default -> {}
        }
    }

    /**
     * Ouvre la fenêtre de messagerie après un login réussi.
     * Charge messaging-view.fxml, initialise MessagingController avec la connexion, le nom et le rôle, affiche la scène.
     * Paramètres : conn – connexion serveur ; username – nom de l'utilisateur ; role – rôle (ex. MEMBRE).
     * Ne renvoie rien. En cas d'IOException affiche l'erreur dans lbl_status.
     */
    private void openMessagingView(ServerConnection conn, String username, String role) {
        try {
            Stage s = stage != null ? stage : (Stage) txt_username.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("messaging-view.fxml"));
            Parent root = loader.load();
            MessagingController ctrl = loader.getController();
            ctrl.init(conn, username, Role.valueOf(role));
            ctrl.setStage(s);

            s.setTitle("Messagerie - " + username);
            s.setScene(new Scene(root, 900, 560));
            s.setOnCloseRequest(e -> conn.disconnect("Déconnexion"));
            s.setMinWidth(400);
            s.setMinHeight(400);
            s.show();

            javafx.application.Platform.runLater(() ->
                javafx.application.Platform.runLater(ctrl::refreshContacts)
            );
        } catch (IOException e) {
            lbl_status.setText("Erreur: " + e.getMessage());
        }
    }

    /**
     * Convertit le texte du champ port en entier.
     * Paramètre : text – chaîne saisie (ex. "9999"). Retourne le numéro de port, ou 9999 si le texte n'est pas un nombre valide.
     */
    private int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 9999;
        }
    }
}
