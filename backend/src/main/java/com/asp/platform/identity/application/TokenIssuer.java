package com.asp.platform.identity.application;

import com.asp.platform.identity.domain.User;

/// Outbound port: signs an access token for a user.
public interface TokenIssuer {

    IssuedToken issue(User user);
}
