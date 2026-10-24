package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.domain.Password;
import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.PlatformAdminRepository;
import com.asp.platform.identity.domain.User;
import com.asp.platform.identity.domain.UserRepository;

/// Creates the first platform admin at startup from `app.platform.bootstrap-admin-email` and
/// `app.platform.bootstrap-admin-password` (environment variables `ASP_BOOTSTRAP_ADMIN_EMAIL` and
/// `ASP_BOOTSTRAP_ADMIN_PASSWORD`), but only while no platform admin exists. Once one exists the settings are
/// ignored, so they can be removed after the first start. Logs the e-mail, never the password.
@Component
class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);
    private static final String DEFAULT_NAME = "Platform admin";

    private final PlatformProperties properties;
    private final PlatformAdminRepository admins;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    PlatformAdminBootstrap(PlatformProperties properties, PlatformAdminRepository admins, UserRepository users,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.admins = admins;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = PlatformProperties.blankToNull(properties.bootstrapAdminEmail());
        String password = properties.bootstrapAdminPassword();
        if (email == null || password == null || password.isEmpty() || admins.existsAny()) {
            return;
        }
        String normalized = User.normalizeEmail(email);
        if (!Password.isAcceptable(password)) {
            log.warn("Platform admin not created: the bootstrap password must have 12 to 72 characters");
            return;
        }
        if (users.existsByEmail(normalized)) {
            log.warn("Platform admin not created: {} already belongs to a workshop user", normalized);
            return;
        }
        String name = PlatformProperties.blankToNull(properties.bootstrapAdminName());
        admins.save(PlatformAdmin.create(UUID.randomUUID(), name == null ? DEFAULT_NAME : name, normalized,
                passwordEncoder.encode(password), clock.instant()));
        log.info("Created the first platform admin {}", normalized);
    }
}
