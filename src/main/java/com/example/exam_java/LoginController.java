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

    public void setConnection(ServerConnection connection) {
        this.connection = connection;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void setStatusStyle(String styleClass) {
        lbl_status.getStyleClass().removeAll("login-status-loading", "login-status-error", "login-status-success");
        if (styleClass != null) lbl_status.getStyleClass().add(styleClass);
    }

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

    private int parsePort(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 9999;
        }
    }
}
