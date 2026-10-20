package com.asp.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.springframework.transaction.annotation.Transactional;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/// Layer rules inside every feature: api -> application -> domain <- infrastructure.
@AnalyzeClasses(packages = "com.asp.platform", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerRulesTests {

    @ArchTest
    static final ArchRule layers_point_inwards = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Api").definedBy("com.asp.platform.*.api..")
            .layer("Application").definedBy("com.asp.platform.*.application..")
            .layer("Domain").definedBy("com.asp.platform.*.domain..")
            .layer("Infrastructure").definedBy("com.asp.platform.*.infrastructure..")
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Api", "Application", "Infrastructure")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule domain_is_framework_free = noClasses()
            .that().resideInAPackage("com.asp.platform.*.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.servlet..", "org.hibernate..",
                    "tools.jackson..", "com.fasterxml.jackson..")
            .because("the domain holds business rules only (Package Structure LAYER)");

    @ArchTest
    static final ArchRule entities_stay_in_infrastructure = noClasses()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().resideOutsideOfPackage("com.asp.platform.*.infrastructure..")
            .because("JPA entities never leave infrastructure");

    @ArchTest
    static final ArchRule transactions_start_in_application = noMethods()
            .that().areAnnotatedWith(Transactional.class)
            .should().beDeclaredInClassesThat().resideOutsideOfPackage("com.asp.platform.*.application..")
            .because("@Transactional boundaries live in the application layer");

    @ArchTest
    static final ArchRule features_do_not_import_config = noClasses()
            .that().resideOutsideOfPackage("com.asp.platform.config..")
            .should().dependOnClassesThat().resideInAPackage("com.asp.platform.config..")
            .because("dependencies go config -> features, never the other way (Package Structure CFG)");
}
