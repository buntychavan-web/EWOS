package com.ewos.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

/**
 * Regression test for a Sprint P9 production-readiness finding: {@code CandidateNumberGenerator}
 * and {@code LeaveRequestService} each had two public constructors with neither {@code @Autowired}
 * nor a no-arg fallback, so Spring's constructor resolution threw {@code
 * NoSuchBeanDefinitionException} at boot. Every Spring-stereotype class in the codebase is scanned
 * here: a class is only safe to autowire if it has exactly one public constructor, or if — having
 * more than one — exactly one of them is annotated {@code @Autowired}.
 */
class ConstructorAmbiguityRegressionTest {

    @Test
    void everySpringStereotypeClassHasAnUnambiguouslyResolvableConstructor() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<String> ambiguous = new ArrayList<>();
        for (var candidate : scanner.findCandidateComponents("com.ewos")) {
            Class<?> type;
            try {
                type = Class.forName(candidate.getBeanClassName());
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                continue;
            }
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            List<Constructor<?>> publicCtors = new ArrayList<>();
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (Modifier.isPublic(c.getModifiers())) {
                    publicCtors.add(c);
                }
            }
            if (publicCtors.size() <= 1) {
                continue;
            }
            long autowiredCount =
                    publicCtors.stream()
                            .filter(c -> c.isAnnotationPresent(Autowired.class))
                            .count();
            if (autowiredCount != 1) {
                ambiguous.add(
                        type.getName()
                                + " ("
                                + publicCtors.size()
                                + " public constructors, "
                                + autowiredCount
                                + " @Autowired)");
            }
        }

        assertThat(ambiguous)
                .as("Spring cannot unambiguously resolve these constructors")
                .isEmpty();
    }
}
