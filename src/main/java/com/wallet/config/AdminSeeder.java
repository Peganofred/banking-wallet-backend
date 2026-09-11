package com.wallet.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.wallet.user.User;
import com.wallet.user.UserRepository;
import com.wallet.user.Role;

/**
 * On startup, if app.admin.auto-create is true and no user with the admin
 * email exists yet, creates the ADMIN account using app.admin.* properties.
 * This gives a guaranteed admin console login without manual DB inserts.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, AdminProperties adminProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!adminProperties.isAutoCreate()) {
            return;
        }
        String email = adminProperties.getEmail().toLowerCase().trim();
        if (email.isBlank()) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(adminProperties.getPassword()))
                .name(adminProperties.getName())
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        //noinspection LoggingPlaceholderCountMatchesArgumentCount
        System.out.println("[AdminSeeder] Created admin account: " + email);
    }
}
