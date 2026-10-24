package com.asp.platform.identity.application;

import com.asp.platform.identity.domain.PlatformAdmin;
import com.asp.platform.identity.domain.User;

/// Outbound port: signs access tokens.
public interface TokenIssuer {

    /// A workshop user's token, carrying their workshop and role.
    IssuedToken issue(User user);

    /// A platform admin's token: role `PLATFORM_ADMIN` and no workshop.
    IssuedToken issue(PlatformAdmin admin);
}
