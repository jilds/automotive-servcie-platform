/// Identity: login, platform admin and subscription onboarding (no public signup), activation links, users and roles,
/// workshop settings and branding.
/// Public API: [TokenClaims], [AccessDirectory] and [UserAccess], [WorkshopDirectory] and [TeamMember].
@ApplicationModule(displayName = "Identity")
@NullMarked
package com.asp.platform.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
