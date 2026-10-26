package com.asp.platform.identity.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asp.platform.identity.LeadReceived;
import com.asp.platform.identity.domain.Lead;
import com.asp.platform.identity.domain.LeadNotFoundException;
import com.asp.platform.identity.domain.LeadRepository;

/// Demo requests: stored from the landing site's form, then worked through by the platform admin. The table is
/// platform-level; only `/api/v1/public-site/leads` writes it and only `/api/v1/admin/leads` reads it.
@Service
public class LeadService {

    /// The admin list shows at most this many requests.
    public static final int LIST_LIMIT = 500;

    private final LeadRepository leads;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    LeadService(LeadRepository leads, ApplicationEventPublisher events, Clock clock) {
        this.leads = leads;
        this.events = events;
        this.clock = clock;
    }

    /// Stores the request and, after the commit, lets the platform admins know ([LeadReceived]).
    @Transactional
    public Lead submit(LeadCommand c) {
        Lead lead = leads.save(Lead.received(UUID.randomUUID(), c.name(), c.workshopName(), c.phone(), c.email(),
                c.district(), c.vehicleType(), c.message(), c.marketingConsent(), clock.instant()));
        events.publishEvent(new LeadReceived(lead.id(), lead.name(), lead.workshopName(), lead.phone(), lead.email(),
                lead.district(), lead.vehicleType().name(), lead.message() != null));
        return lead;
    }

    /// Newest first, only `status` when given, at most [#LIST_LIMIT].
    @Transactional(readOnly = true)
    public List<Lead> list(Lead.@Nullable Status status) {
        return leads.list(status, LIST_LIMIT);
    }

    /// @throws LeadNotFoundException                     for an unknown id
    /// @throws com.asp.platform.shared.StaleDataException when someone else changed it since it was read
    @Transactional
    public Lead changeStatus(UUID id, Lead.Status status, long expectedVersion) {
        Lead lead = leads.findById(id).orElseThrow(LeadNotFoundException::new);
        return leads.save(lead.withStatus(status, expectedVersion, clock.instant()));
    }
}
