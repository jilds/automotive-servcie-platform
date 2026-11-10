package com.asp.platform.billing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;


import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/// Dependency direction of the billing module (plan 07): it reads from workshop, customer and identity through their
/// facades; nobody reads from billing. And the sandbox never runs in production.
class BillingArchitectureTests {

    @Test
    void no_other_module_depends_on_billing() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.asp.platform");

        noClasses().that().resideOutsideOfPackages("com.asp.platform.billing..", "com.asp.platform.config..")
                .should().dependOnClassesThat().resideInAPackage("com.asp.platform.billing..")
                .because("billing reads delivered work orders through a facade of workshop, never the reverse")
                .check(classes);
    }

    @Test
    void billing_uses_other_modules_through_their_root_package_only() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.asp.platform");

        noClasses().that().resideInAPackage("com.asp.platform.billing..")
                .should().dependOnClassesThat().resideInAnyPackage("com.asp.platform.workshop.api..",
                        "com.asp.platform.workshop.application..", "com.asp.platform.workshop.domain..",
                        "com.asp.platform.workshop.infrastructure..", "com.asp.platform.customer.api..",
                        "com.asp.platform.customer.application..", "com.asp.platform.customer.domain..",
                        "com.asp.platform.customer.infrastructure..", "com.asp.platform.identity.api..",
                        "com.asp.platform.identity.application..", "com.asp.platform.identity.domain..",
                        "com.asp.platform.identity.infrastructure..", "com.asp.platform.stock..",
                        "com.asp.platform.shop..")
                .check(classes);
    }
}
