package com.asp.platform.reporting;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/// Plan 08: `reporting` reads tables, not other modules, and nobody depends on it. It is read-only.
class ReportingArchitectureTests {

    /// Everything reporting may depend on (ADR 0005). `com.asp.platform.shared` is that one package, not
    /// `shared.persistence` (JPA helpers); `com.fasterxml.jackson.annotation` is the annotations package Jackson 3 still
    /// uses (`@JsonProperty`).
    private static final String[] ALLOWED = {
            "com.asp.platform.reporting..", "com.asp.platform.shared", "java..", "org.springframework..",
            "org.jspecify..", "jakarta.validation..", "tools.jackson..", "com.fasterxml.jackson.annotation..",
            "org.slf4j.." };

    private final com.tngtech.archunit.core.domain.JavaClasses imported = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.asp.platform");

    @Test
    void should_depend_only_on_itself_shared_and_the_framework() {
        classes().that().resideInAPackage("com.asp.platform.reporting..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(ALLOWED)
                .because("reports read tables (row-level security keeps them per workshop), never other modules "
                        + "(ADR 0005); an allow-list so that a new module is refused by default")
                .check(imported);
    }

    @Test
    void should_not_be_used_by_any_other_module() {
        noClasses().that().resideOutsideOfPackages("com.asp.platform.reporting..", "com.asp.platform.config..")
                .should().dependOnClassesThat().resideInAPackage("com.asp.platform.reporting..")
                .check(imported);
    }

    @Test
    void should_not_use_jpa_or_write_through_repositories() {
        noClasses().that().resideInAPackage("com.asp.platform.reporting..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..",
                        "org.springframework.data..")
                .because("reporting is read-only JDBC")
                .check(imported);
    }

    @Test
    void should_use_the_jdbc_client_not_the_jdbc_template() {
        noClasses().that().resideInAPackage("com.asp.platform.reporting..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
                .because("JdbcClient with read-only transactions is the one way in")
                .check(imported);
    }
}
