package com.asp.platform.identity.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.PlatformTeam;
import com.asp.platform.identity.domain.PlatformAdminRepository;

/// The platform_admin table is platform-level (no row-level security), so this works outside any request.
@Service
class PlatformTeamService implements PlatformTeam {

    private final PlatformAdminRepository admins;

    PlatformTeamService(PlatformAdminRepository admins) {
        this.admins = admins;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> activeAdminEmails() {
        return admins.activeEmails();
    }
}
