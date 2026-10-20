package com.asp.platform.identity.application;

import java.time.Duration;

public record IssuedToken(String value, Duration ttl) {
}
