package com.ewos.integration.infrastructure.adapter;

import com.ewos.integration.domain.IntegrationAdapter;
import com.ewos.integration.domain.IntegrationAdapterType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Resolves the one {@link IntegrationAdapter} bean registered for each {@link IntegrationAdapterType}. */
@Component
public class IntegrationAdapterRegistry {

    private final Map<IntegrationAdapterType, IntegrationAdapter> byType;

    public IntegrationAdapterRegistry(List<IntegrationAdapter> adapters) {
        this.byType = new EnumMap<>(IntegrationAdapterType.class);
        for (IntegrationAdapter adapter : adapters) {
            byType.put(adapter.type(), adapter);
        }
    }

    public Optional<IntegrationAdapter> find(IntegrationAdapterType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
