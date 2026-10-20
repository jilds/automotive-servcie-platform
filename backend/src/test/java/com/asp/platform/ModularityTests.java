package com.asp.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/// Fails the build when one module reaches into another module's internals or modules form a cycle.
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(AspApplication.class);

    @Test
    void should_respect_module_boundaries() {
        modules.verify();
    }

    @Test
    void should_write_module_documentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
