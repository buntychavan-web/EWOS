package com.ewos.dashboard.application;

import com.ewos.dashboard.api.dto.DashboardSummaryResponse;
import com.ewos.dashboard.infrastructure.DashboardCountsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads the aggregate counters via a single native query. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final DashboardCountsRepository repository;

    public DashboardService(DashboardCountsRepository repository) {
        this.repository = repository;
    }

    public DashboardSummaryResponse summary() {
        long[] c = repository.loadCounts();
        return new DashboardSummaryResponse(c[0], c[1], c[2], c[3]);
    }
}
