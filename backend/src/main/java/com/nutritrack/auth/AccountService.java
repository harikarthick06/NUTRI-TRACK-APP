package com.nutritrack.auth;

import com.nutritrack.common.ApiException;
import com.nutritrack.security.CurrentUser;
import com.nutritrack.user.User;
import com.nutritrack.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final EntityManager em;

    public AccountService(UserRepository users, PasswordEncoder passwordEncoder, AuthService authService,
                          EntityManager em) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.em = em;
    }

    @Transactional
    public AuthService.Session changePassword(String currentPassword, String newPassword) {
        User user = currentUserVerified(currentPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("SAME_PASSWORD", "New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return authService.restartSessions(user);
    }

    /**
     * Deletes the user and everything they own. Rows are removed explicitly in dependency order (diary
     * entries before the custom foods they reference) rather than relying on cascade ordering.
     */
    @Transactional
    public void deleteAccount(String password) {
        User user = currentUserVerified(password);
        long id = user.getId();
        String[] statements = {
                "DELETE FROM food_diary_entries WHERE user_id = :id",
                "DELETE FROM saved_meals WHERE user_id = :id",
                "DELETE ri FROM recipe_ingredients ri JOIN recipes r ON r.id = ri.recipe_id WHERE r.owner_user_id = :id",
                "UPDATE foods SET recipe_id = NULL WHERE owner_user_id = :id",
                "DELETE FROM recipes WHERE owner_user_id = :id",
                "DELETE FROM user_favorite_foods WHERE user_id = :id",
                "DELETE FROM foods WHERE owner_user_id = :id",
                "DELETE FROM water_logs WHERE user_id = :id",
                "DELETE FROM weight_logs WHERE user_id = :id",
                "DELETE FROM user_nutrient_targets WHERE user_id = :id",
                "DELETE FROM diary_days WHERE user_id = :id",
                "DELETE FROM refresh_tokens WHERE user_id = :id",
                "DELETE FROM user_profiles WHERE user_id = :id",
                "DELETE FROM users WHERE id = :id",
        };
        for (String sql : statements) {
            em.createNativeQuery(sql).setParameter("id", id).executeUpdate();
        }
    }

    private User currentUserVerified(String password) {
        User user = users.findById(CurrentUser.id()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.forbidden("Password is incorrect");
        }
        return user;
    }
}
