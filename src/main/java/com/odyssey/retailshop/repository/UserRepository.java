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
