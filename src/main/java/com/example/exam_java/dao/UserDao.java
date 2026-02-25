package com.example.exam_java.dao;

import com.example.exam_java.entity.Role;
import com.example.exam_java.entity.User;
import com.example.exam_java.util.PasswordUtil;
import com.example.exam_java.entity.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.NoResultException;

import java.util.List;

/**
 * DAO pour l'entité User - RG1: username unique
 */
public class UserDao {

    private final EntityManagerFactory emf = JpaUtil.getEntityManagerFactory();

    public void insert(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        em.close();
    }

    public User findById(Long id) {
        EntityManager em = emf.createEntityManager();
        User user = em.find(User.class, id);
        em.close();
        return user;
    }

    public User findByUsername(String username) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    public List<User> findAll() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class).getResultList();
        em.close();
        return list;
    }

    /**
     * RG13: Liste complète des membres (pour ORGANISATEUR)
     */
    public List<User> findAllMembers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class).getResultList();
        em.close();
        return list;
    }

    public List<User> findOnlineUsers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
                .setParameter("status", UserStatus.ONLINE)
                .getResultList();
        em.close();
        return list;
    }

    public void update(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(user);
        em.getTransaction().commit();
        em.close();
    }

    public void updateStatus(String username, UserStatus status) {
        User user = findByUsername(username);
        if (user != null) {
            user.setStatus(status);
            update(user);
        }
    }

    public List<User> findPendingUsers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u WHERE u.validated = false ORDER BY u.dateCreation", User.class).getResultList();
        em.close();
        return list;
    }

    public void validateUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setValidated(true);
            update(user);
        }
    }

    public void blockUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setBlocked(true);
            update(user);
        }
    }

    public void unblockUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setBlocked(false);
            update(user);
        }
    }

    /** Supprime l'utilisateur par son username (pour admin). */
    public boolean deleteUserByUsername(String username) {
        EntityManager em = emf.createEntityManager();
        try {
            User u = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username).getSingleResult();
            em.getTransaction().begin();
            em.remove(em.merge(u));
            em.getTransaction().commit();
            return true;
        } catch (NoResultException e) {
            return false;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            return false;
        } finally {
            em.close();
        }
    }

    /**
     * Crée les utilisateurs par défaut au démarrage si inexistants.
     * - admin / admin
     * - user1 / user123
     * - user2 / user123
     */
    public void ensureDefaultUsersExist() {
        if (!existsByUsername("admin")) {
            User admin = new User("admin", PasswordUtil.hash("admin"), Role.ORGANISATEUR);
            admin.setValidated(true);
            insert(admin);
        }
        if (!existsByUsername("user1")) {
            User u1 = new User("user1", PasswordUtil.hash("user123"), Role.MEMBRE);
            u1.setValidated(true);
            insert(u1);
        }
        if (!existsByUsername("user2")) {
            User u2 = new User("user2", PasswordUtil.hash("user123"), Role.MEMBRE);
            u2.setValidated(true);
            insert(u2);
        }
    }
}
