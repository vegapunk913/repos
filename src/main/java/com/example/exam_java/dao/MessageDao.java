package com.example.exam_java.dao;

import com.example.exam_java.entity.Message;
import com.example.exam_java.entity.MessageStatut;
import com.example.exam_java.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;

/**
 * DAO pour l'entité Message - RG8: historique par ordre chronologique
 */
public class MessageDao {

    private final EntityManagerFactory emf = JpaUtil.getEntityManagerFactory();

    public void insert(Message message) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        message.setStatut(MessageStatut.ENVOYE);
        em.persist(message);
        em.getTransaction().commit();
        em.close();
    }

    public Message findById(Long id) {
        EntityManager em = emf.createEntityManager();
        Message msg = em.find(Message.class, id);
        em.close();
        return msg;
    }

    /**
     * RG8: Historique des messages entre deux utilisateurs, ordre chronologique
     */
    public List<Message> findConversation(User user1, User user2) {
        EntityManager em = emf.createEntityManager();
        List<Message> list = em.createQuery("""
                SELECT m FROM Message m
                WHERE (m.sender = :u1 AND m.receiver = :u2) OR (m.sender = :u2 AND m.receiver = :u1)
                ORDER BY m.dateEnvoi ASC
                """, Message.class)
                .setParameter("u1", user1)
                .setParameter("u2", user2)
                .getResultList();
        em.close();
        return list;
    }

    /**
     * Messages en attente pour un utilisateur (RG6: livrés à la prochaine connexion)
     */
    public List<Message> findPendingForUser(User receiver) {
        EntityManager em = emf.createEntityManager();
        List<Message> list = em.createQuery("""
                SELECT m FROM Message m
                WHERE m.receiver = :receiver AND m.statut = :statut
                ORDER BY m.dateEnvoi ASC
                """, Message.class)
                .setParameter("receiver", receiver)
                .setParameter("statut", MessageStatut.ENVOYE)
                .getResultList();
        em.close();
        return list;
    }

    public void update(Message message) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(message);
        em.getTransaction().commit();
        em.close();
    }

    public void markAsReceived(Message message) {
        message.setStatut(MessageStatut.RECU);
        update(message);
    }

    public void markAsRead(Message message) {
        message.setStatut(MessageStatut.LU);
        update(message);
    }

    /** Supprime tous les messages où l'utilisateur (par username) est expéditeur ou destinataire (avant suppression du compte). */
    public void deleteByUsername(String username) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        try {
            em.createQuery("DELETE FROM Message m WHERE m.sender.username = :un OR m.receiver.username = :un")
                    .setParameter("un", username)
                    .executeUpdate();
        } finally {
            em.getTransaction().commit();
            em.close();
        }
    }
}
