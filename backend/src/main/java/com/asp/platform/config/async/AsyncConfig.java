package com.asp.platform.config.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/// Turns on `@Async`, used by the notification listeners so a mail is sent off the request thread after the commit.
/// With `spring.threads.virtual.enabled` the executor runs each task on a virtual thread.
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfig {
}
