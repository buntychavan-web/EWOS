package com.ewos.offer.infrastructure.employeeid;

import com.ewos.offer.domain.EmployeeIdGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Default {@link EmployeeIdGenerator} — produces {@code EMP-YYYYMM-XXXXXX} tokens. Deployments with
 * an existing HRIS numbering convention plug in a {@code @Primary} bean.
 */
@Component
public class SequentialEmployeeIdGenerator implements EmployeeIdGenerator {

    private static final String PROVIDER = "sequential-employee-id";

    private final Clock clock;

    public SequentialEmployeeIdGenerator() {
        this(Clock.systemUTC());
    }

    SequentialEmployeeIdGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String generate(UUID tenantId, UUID companyId) {
        LocalDate today = LocalDate.now(clock);
        return String.format(
                "EMP-%04d%02d-%06d",
                today.getYear(),
                today.getMonthValue(),
                ThreadLocalRandom.current().nextInt(0, 1_000_000));
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }
}
