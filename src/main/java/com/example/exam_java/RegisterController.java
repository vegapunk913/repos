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

    @FXML
    public void initialize() {
        cbx_role.getItems().addAll("MEMBRE", "BENEVOLE", "ORGANISATEUR");
        cbx_role.getSelectionModel().selectFirst();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setHostPort(String host, String port) {
        this.host = host != null ? host : "localhost";
        this.port = port != null ? port : "9999";
    }

    private void setStatusStyle(String styleClass) {
        lbl_status.getStyleClass().removeAll("login-status-loading", "login-status-error", "login-status-success");
        if (styleClass != null) lbl_status.getStyleClass().add(styleClass);
    }

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

    private int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 9999;
        }
    }
}
