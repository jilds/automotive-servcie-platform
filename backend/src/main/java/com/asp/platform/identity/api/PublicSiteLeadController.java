package com.asp.platform.identity.api;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asp.platform.identity.application.LeadService;
import com.asp.platform.shared.PrivacyConsentRequiredException;

/// The landing site's demo request form ("Pedir proposta"). Anonymous; its own security chain allows CORS from the
/// site's origin only, and each client address may send a few requests an hour (`app.public-site.leads`).
@RestController
@RequestMapping("/api/v1/public-site")
class PublicSiteLeadController {

    private static final Logger log = LoggerFactory.getLogger(PublicSiteLeadController.class);

    private final LeadService leads;

    PublicSiteLeadController(LeadService leads) {
        this.leads = leads;
    }

    /// 201 with no body. A filled hidden `website` field is a bot: answered the same way, nothing stored. The privacy
    /// check comes first, so a bot cannot tell the hidden field from the answers.
    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.CREATED)
    void submit(@Valid @RequestBody LeadRequest request) {
        if (!request.acceptedPrivacy()) {
            throw new PrivacyConsentRequiredException();
        }
        if (request.honeypotFilled()) {
            log.info("Demo request dropped: hidden field filled");
            return;
        }
        leads.submit(request.toCommand());
    }
}
