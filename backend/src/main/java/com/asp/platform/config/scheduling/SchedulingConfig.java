package com.asp.platform.config.scheduling;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/// Turns on `@Scheduled` jobs (the first is the shop's `ShopOrderExpiry`) and ShedLock, so with several instances
/// only one runs a job at a time. Locks live in the platform table `shedlock` (V17) and use the database's clock,
/// so instances with drifting clocks agree. The connection binds no workshop (no token, no scope), and the table
/// has none. Off when `app.scheduling.enabled` is false (the tests run jobs directly).
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "app.scheduling.enabled", matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
