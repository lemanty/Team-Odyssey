package com.odyssey.retailshop.repository;

import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(ObjectProvider<JdbcTemplate> provider) { this.jdbc = provider.getIfAvailable(); }
    public record Account(long id, String displayName, String email, String passwordHash, String role) {}
    public boolean available() { return jdbc != null; }
    public Optional<Account> findByLogin(String identifier) {
        if (!available()) return Optional.empty();
        // Email identifiers and staff aliases occupy separate namespaces.
        if (identifier.contains("@")) return findByEmail(identifier);
        return jdbc.query("SELECT u.email FROM users u JOIN user_login_names n ON n.user_id=u.id WHERE n.login_name = ?",
            (r,n) -> r.getString("email"), identifier).stream().findFirst().flatMap(this::findByEmail);
    }
    public Optional<Account> findByEmail(String email) {
        if (!available()) return Optional.empty();
        return jdbc.query("SELECT id, display_name, email, password_hash, role FROM users WHERE email = ?",
            (r,n) -> new Account(r.getLong("id"), r.getString("display_name"), r.getString("email"), r.getString("password_hash"), r.getString("role")), email).stream().findFirst();
    }
    public void create(String name, String email, String hash) {
        if (!available()) throw new IllegalStateException("Database profile required");
        jdbc.update("INSERT INTO users(display_name,email,password_hash,role) VALUES (?,?,?,'CUSTOMER')", name, email, hash);
    }
}
