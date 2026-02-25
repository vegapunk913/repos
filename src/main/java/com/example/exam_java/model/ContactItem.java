package com.example.exam_java.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Représente un contact dans la liste (avec statut, rôle et notifications)
 */
public class ContactItem {

    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();  // ONLINE / OFFLINE
    private final StringProperty role = new SimpleStringProperty();   // ORGANISATEUR, MEMBRE, BENEVOLE
    private final IntegerProperty unreadCount = new SimpleIntegerProperty(0);

    public ContactItem(String username, String status) {
        this(username, status, null);
    }

    public ContactItem(String username, String status, String role) {
        this.username.set(username);
        this.status.set(status);
        this.role.set(role != null ? role : "MEMBRE");
    }

    public String getUsername() { return username.get(); }
    public StringProperty usernameProperty() { return username; }
    public void setUsername(String u) { username.set(u); }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String s) { status.set(s); }

    public String getRole() { return role.get(); }
    public StringProperty roleProperty() { return role; }
    public void setRole(String r) { role.set(r); }

    /** Libellé affiché pour le rôle (Organisateur, Membre, Bénévole) */
    public String getRoleLabel() {
        return switch (getRole() == null ? "" : getRole().toUpperCase()) {
            case "ORGANISATEUR" -> "Organisateur";
            case "BENEVOLE" -> "Bénévole";
            default -> "Membre";
        };
    }

    public int getUnreadCount() { return unreadCount.get(); }
    public IntegerProperty unreadCountProperty() { return unreadCount; }
    public void setUnreadCount(int c) { unreadCount.set(c); }
    public void incrementUnread() { unreadCount.set(unreadCount.get() + 1); }
    public void clearUnread() { unreadCount.set(0); }

    public boolean isOnline() { return "ONLINE".equals(status.get()); }
}
