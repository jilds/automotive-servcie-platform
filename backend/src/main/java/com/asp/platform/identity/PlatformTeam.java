package com.asp.platform.identity;

import java.util.List;

/// Public API of the identity module about the ASP team itself (the platform admins), for messages addressed to
/// them.
public interface PlatformTeam {

    /// The e-mail addresses of the active platform admins.
    List<String> activeAdminEmails();
}
