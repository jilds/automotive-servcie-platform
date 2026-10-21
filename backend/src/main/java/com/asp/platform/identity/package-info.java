/// Identity: workshop signup, login, users and roles, workshop settings.
/// Public API: [TokenClaims], [AccessDirectory] and [UserAccess], [WorkshopDirectory] and [TeamMember].
@ApplicationModule(displayName = "Identity")
@NullMarked
package com.asp.platform.identity;

import org.jspecify.annotations.NullMarked;
import org.springframework.modulith.ApplicationModule;
