package com.example.exam_java.model;

import javafx.beans.property.*;

/**
 * Ligne affichée dans le tableau de gestion des utilisateurs (admin).
 */
public class AdminUserRow {

    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty role = new SimpleStringProperty();
    private final BooleanProperty validated = new SimpleBooleanProperty();
    private final BooleanProperty blocked = new SimpleBooleanProperty();
    private final StringProperty compteActif = new SimpleStringProperty(); // "Actif" ou "Inactif"
    private final StringProperty status = new SimpleStringProperty(); // "En ligne" / "Hors ligne"

    public AdminUserRow(String username, String role, boolean validated, boolean blocked, boolean online) {
        this.username.set(username);
        this.role.set(role);
        this.validated.set(validated);
        this.blocked.set(blocked);
        this.compteActif.set(validated && !blocked ? "Actif" : "Inactif");
        this.status.set(online ? "En ligne" : "Hors ligne");
    }

    public String getUsername() { return username.get(); }
    public StringProperty usernameProperty() { return username; }
    public String getRole() { return role.get(); }
    public StringProperty roleProperty() { return role; }
    public boolean isValidated() { return validated.get(); }
    public BooleanProperty validatedProperty() { return validated; }
    public boolean isBlocked() { return blocked.get(); }
    public BooleanProperty blockedProperty() { return blocked; }
    public String getCompteActif() { return compteActif.get(); }
    public StringProperty compteActifProperty() { return compteActif; }
    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
}
