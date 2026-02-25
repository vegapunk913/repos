package com.example.exam_java.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Entité Message - RG7: contenu max 1000 caractères
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(length = 1000)
    private String contenu;  // Texte ou "[FICHIER] nom" pour les fichiers

    @Column(name = "date_envoi", nullable = false)
    private LocalDateTime dateEnvoi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MessageStatut statut = MessageStatut.ENVOYE;

    @Column(name = "is_file")
    private boolean isFile = false;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 512)
    private String filePath;

    public Message() {
    }

    public Message(User sender, User receiver, String contenu) {
        this.sender = sender;
        this.receiver = receiver;
        this.contenu = contenu;
        this.dateEnvoi = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public User getReceiver() {
        return receiver;
    }

    public void setReceiver(User receiver) {
        this.receiver = receiver;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public LocalDateTime getDateEnvoi() {
        return dateEnvoi;
    }

    public void setDateEnvoi(LocalDateTime dateEnvoi) {
        this.dateEnvoi = dateEnvoi;
    }

    public MessageStatut getStatut() {
        return statut;
    }

    public void setStatut(MessageStatut statut) {
        this.statut = statut;
    }

    public boolean isFile() { return isFile; }
    public void setFile(boolean file) { isFile = file; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    @Override
    public String toString() {
        return sender.getUsername() + " -> " + receiver.getUsername() + ": " + contenu;
    }
}
