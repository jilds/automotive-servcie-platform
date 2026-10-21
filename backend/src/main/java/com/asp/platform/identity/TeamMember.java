package com.asp.platform.identity;

import java.util.UUID;

/// What other modules may know about a workshop user: no email, no credentials.
public record TeamMember(UUID id, String name) {
}
