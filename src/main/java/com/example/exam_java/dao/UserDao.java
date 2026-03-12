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

    /**
     * Insère un nouvel utilisateur en base (RG1 : username unique géré par le contrôleur/serveur).
     * Paramètre : user – entité User à persister. Ne renvoie rien. Lève une exception si contrainte violée.
     */
    public void insert(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        em.close();
    }

    /**
     * Recherche un utilisateur par son identifiant.
     * Paramètre : id – identifiant. Retourne l'User ou null si non trouvé.
     */
    public User findById(Long id) {
        EntityManager em = emf.createEntityManager();
        User user = em.find(User.class, id);
        em.close();
        return user;
    }

    /**
     * Recherche un utilisateur par son nom d'utilisateur.
     * Paramètre : username – nom unique. Retourne l'User ou null si aucun résultat.
     */
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

    /**
     * Indique si un utilisateur existe avec ce nom.
     * Paramètre : username – nom à tester. Retourne true si trouvé, false sinon.
     */
    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    /**
     * Retourne tous les utilisateurs triés par username.
     * Aucun paramètre. Retourne la liste (jamais null).
     */
    public List<User> findAll() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class).getResultList();
        em.close();
        return list;
    }

    /**
     * Liste complète des membres (pour ORGANISATEUR – RG13), triés par username.
     * Aucun paramètre. Retourne la liste (jamais null).
     */
    public List<User> findAllMembers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u ORDER BY u.username", User.class).getResultList();
        em.close();
        return list;
    }

    /**
     * Liste les utilisateurs dont le statut est ONLINE.
     * Aucun paramètre. Retourne la liste (jamais null).
     */
    public List<User> findOnlineUsers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
                .setParameter("status", UserStatus.ONLINE)
                .getResultList();
        em.close();
        return list;
    }

    /**
     * Met à jour un utilisateur existant en base (merge).
     * Paramètre : user – entité détachée à mettre à jour. Ne renvoie rien.
     */
    public void update(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.merge(user);
        em.getTransaction().commit();
        em.close();
    }

    /**
     * Met à jour le statut (ONLINE/OFFLINE) d'un utilisateur par son username.
     * Paramètres : username – nom ; status – nouveau statut. Ne renvoie rien. Ne fait rien si l'utilisateur n'existe pas.
     */
    public void updateStatus(String username, UserStatus status) {
        User user = findByUsername(username);
        if (user != null) {
            user.setStatus(status);
            update(user);
        }
    }

    /**
     * Liste les utilisateurs en attente de validation (validated = false), triés par date de création.
     * Aucun paramètre. Retourne la liste (jamais null).
     */
    public List<User> findPendingUsers() {
        EntityManager em = emf.createEntityManager();
        List<User> list = em.createQuery("SELECT u FROM User u WHERE u.validated = false ORDER BY u.dateCreation", User.class).getResultList();
        em.close();
        return list;
    }

    /**
     * Marque un utilisateur comme validé (peut se connecter).
     * Paramètre : username – nom de l'utilisateur. Ne renvoie rien. Ne fait rien si l'utilisateur n'existe pas.
     */
    public void validateUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setValidated(true);
            update(user);
        }
    }

    /**
     * Bloque un utilisateur (ne peut plus se connecter).
     * Paramètre : username – nom. Ne renvoie rien. Ne fait rien si l'utilisateur n'existe pas.
     */
    public void blockUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setBlocked(true);
            update(user);
        }
    }

    /**
     * Débloque un utilisateur.
     * Paramètre : username – nom. Ne renvoie rien. Ne fait rien si l'utilisateur n'existe pas.
     */
    public void unblockUser(String username) {
        User user = findByUsername(username);
        if (user != null) {
            user.setBlocked(false);
            update(user);
        }
    }

    /**
     * Supprime définitivement l'utilisateur par son username (pour admin). À appeler après suppression des messages associés.
     * Paramètre : username – nom. Retourne true si supprimé, false si utilisateur inexistant ou erreur.
     */
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
