package com.asp.platform;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.asp.platform.shared.PublicWorkshopRef;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/// ADR 0004: every handler under `/api/v1/public/**` takes a [PublicWorkshopRef], so it works for the workshop of the
/// slug and nothing else. Checked on the production classes (the booking pages; allowed to be empty so the rule
/// never fails for lack of handlers), on the test-only probe controller, and on a deliberately bad fixture.
class PublicApiRulesTests {

    static final String PUBLIC_PREFIX = "/api/v1/public";

    static final ArchRule public_handlers_take_the_workshop = methods()
            .that(arePublicHandlers())
            .should(takeAPublicWorkshopRef())
            .because("public pages must be served for the workshop of the slug (ADR 0004)")
            .allowEmptyShould(true);

    @Test
    void production_public_handlers_take_the_workshop() {
        JavaClasses production = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.asp.platform");

        public_handlers_take_the_workshop.check(production);
    }

    @Test
    void the_test_probe_controller_follows_the_rule() {
        JavaClasses withTests = new ClassFileImporter()
                .withImportOption(location -> !location.contains("PublicApiRulesTests"))
                .importPackages("com.asp.platform");

        // Not vacuous: the probe controller's handlers are found ...
        assertThat(withTests.stream().flatMap(type -> type.getMethods().stream()).filter(arePublicHandlers()))
                .extracting(JavaMethod::getName).contains("probe", "write", "booking");
        // ... and pass.
        public_handlers_take_the_workshop.check(withTests);
    }

    @Test
    void a_public_handler_without_the_workshop_breaks_the_rule() {
        JavaClasses bad = new ClassFileImporter().importClasses(BadPublicController.class);

        var result = public_handlers_take_the_workshop.evaluate(bad);

        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(line -> assertThat(line).contains("withoutWorkshop"))
                .noneSatisfy(line -> assertThat(line).contains("withWorkshop("))
                .noneSatisfy(line -> assertThat(line).contains("notPublic"));
    }

    /// Handler methods whose full path (class mapping + method mapping) is under [#PUBLIC_PREFIX].
    private static DescribedPredicate<JavaMethod> arePublicHandlers() {
        return DescribedPredicate.describe("are request handlers under " + PUBLIC_PREFIX + "/**",
                method -> paths(method).anyMatch(path -> path.equals(PUBLIC_PREFIX)
                        || path.startsWith(PUBLIC_PREFIX + "/")));
    }

    private static ArchCondition<JavaMethod> takeAPublicWorkshopRef() {
        return new ArchCondition<>("take a " + PublicWorkshopRef.class.getSimpleName() + " parameter") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean takesIt = method.getRawParameterTypes().stream()
                        .anyMatch(type -> type.isEquivalentTo(PublicWorkshopRef.class));
                events.add(new SimpleConditionEvent(method, takesIt,
                        method.getFullName() + (takesIt ? " takes " : " does not take ")
                                + PublicWorkshopRef.class.getSimpleName()));
            }
        };
    }

    private static Stream<String> paths(JavaMethod javaMethod) {
        Method method;
        try {
            method = javaMethod.reflect();
        } catch (RuntimeException | LinkageError e) {
            return Stream.empty();
        }
        RequestMapping onMethod = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        if (onMethod == null) {
            return Stream.empty();
        }
        RequestMapping onClass = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(),
                RequestMapping.class);
        List<String> prefixes = onClass == null ? List.of("") : orRoot(onClass.path());
        List<String> suffixes = orRoot(onMethod.path());
        return prefixes.stream().flatMap(prefix -> suffixes.stream().map(suffix -> join(prefix, suffix)));
    }

    private static List<String> orRoot(String[] paths) {
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
    }

    private static String join(String prefix, String suffix) {
        String left = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        String right = suffix.isEmpty() || suffix.startsWith("/") ? suffix : "/" + suffix;
        String path = left + right;
        return path.startsWith("/") ? path : "/" + path;
    }

    /// Not a `@RestController`, so Spring never registers it; only the rule reads its mappings.
    @RequestMapping("/api/v1/public/{slug}")
    static class BadPublicController {

        @GetMapping("/bad")
        String withoutWorkshop() {
            return "";
        }

        @PostMapping("/good")
        String withWorkshop(PublicWorkshopRef workshop) {
            return workshop.name();
        }

        String notPublic() {
            return "";
        }
    }
}
