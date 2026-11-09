package com.asp.platform.workshop.application;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// `app.photos.*`.
///
/// @param maxBytesPerWorkshop    the bytes of photos one workshop may keep (deleted photos count until their bytes are
///                               purged); an upload beyond it answers 409 `photo-storage-full`. 5 GiB by default;
///                               product tiers will set it per plan later.
/// @param deletedRetentionDays   days after a delete before the photo's bytes are dropped (the audit row stays)
@Validated
@ConfigurationProperties("app.photos")
public record PhotoProperties(
        @DefaultValue("5368709120") @Min(1) long maxBytesPerWorkshop,
        @DefaultValue("30") @Min(0) int deletedRetentionDays) {
}
