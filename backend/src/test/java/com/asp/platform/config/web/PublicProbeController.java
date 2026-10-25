package com.asp.platform.config.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.catalogue.domain.LabourRate;
import com.asp.platform.shared.PublicWorkshopRef;
import com.asp.platform.shared.TenantScope;

/// Test-only public endpoint: shows which workshop a public request is served for and what row-level security lets
/// it see. Opens a transaction like a feature's application service would, after the filter bound the scope.
@RestController
class PublicProbeController {

    private final JdbcClient jdbc;
    private final LabourRate labourRate;
    private final TransactionTemplate readOnly;

    PublicProbeController(JdbcClient jdbc, LabourRate labourRate, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.labourRate = labourRate;
        this.readOnly = new TransactionTemplate(transactions);
        this.readOnly.setReadOnly(true);
    }

    @GetMapping("/api/v1/public/{slug}/probe")
    Map<String, Object> probe(PublicWorkshopRef workshop) {
        return Objects.requireNonNull(readOnly.execute(_ -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("tenantId", workshop.tenantId().toString());
            body.put("scope", TenantScope.current().map(Object::toString).orElse(null));
            body.put("connectionTenant", jdbc.sql("select current_setting('app.tenant_id', true)")
                    .query(String.class).single());
            body.put("backendPid", jdbc.sql("select pg_backend_pid()").query(Integer.class).single());
            body.put("customers", jdbc.sql("select count(*) from customer").query(Long.class).single());
            body.put("labourRateCents", labourRate.hourlyCents());
            body.put("name", workshop.name());
            body.put("timeZone", workshop.timeZone().getId());
            body.put("booking", workshop.booking());
            body.put("shop", workshop.shop());
            return body;
        }));
    }

    @PostMapping("/api/v1/public/{slug}/probe")
    Map<String, Object> write(PublicWorkshopRef workshop) {
        return Map.of("tenantId", workshop.tenantId().toString());
    }

    @GetMapping("/api/v1/public/{slug}/probe/booking")
    Map<String, Object> booking(PublicWorkshopRef workshop) {
        workshop.requireBooking();
        return Map.of("booking", true);
    }
}
