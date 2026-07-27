package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterResult;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationAdapterRegistryTest {

    private static final class StubAdapter implements IntegrationAdapter {
        private final IntegrationAdapterType type;

        private StubAdapter(IntegrationAdapterType type) {
            this.type = type;
        }

        @Override
        public IntegrationAdapterType type() {
            return type;
        }

        @Override
        public IntegrationAdapterResult execute(IntegrationExecutionContext context) {
            return IntegrationAdapterResult.success("stub");
        }
    }

    @Test
    void resolvesAdaptersByType() {
        IntegrationAdapter csv = new StubAdapter(IntegrationAdapterType.CSV);
        IntegrationAdapter rest = new StubAdapter(IntegrationAdapterType.REST);
        IntegrationAdapterRegistry registry = new IntegrationAdapterRegistry(List.of(csv, rest));

        assertThat(registry.find(IntegrationAdapterType.CSV)).contains(csv);
        assertThat(registry.find(IntegrationAdapterType.REST)).contains(rest);
    }

    @Test
    void returnsEmptyForAnUnregisteredType() {
        IntegrationAdapterRegistry registry =
                new IntegrationAdapterRegistry(
                        List.of(new StubAdapter(IntegrationAdapterType.CSV)));

        assertThat(registry.find(IntegrationAdapterType.SFTP)).isEmpty();
    }
}
