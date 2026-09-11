package com.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /** Seed this e-mail as an ADMIN on startup (admin account auto-created). */
    private String email = "admin@wallet.dev";

    /** Password for the seeded admin (BCrypt-encoded at startup). Plain here for dev. */
    private String password = "admin1234";

    /** Display name for the seeded admin. */
    private String name = "Platform Admin";

    /** When false, no admin is auto-created (e.g. prod). */
    private boolean autoCreate = true;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isAutoCreate() { return autoCreate; }
    public void setAutoCreate(boolean autoCreate) { this.autoCreate = autoCreate; }
}
