package com.ewos.dashboard.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Single-roundtrip aggregate counts for the dashboard. Employees and Departments columns are
 * placeholders — when those modules land, replace the constant {@code 0} in the query with the
 * matching {@code SELECT COUNT(*)} against their live rows.
 */
@Repository
public class DashboardCountsRepository {

    private static final String COUNTS_SQL =
            "SELECT "
                    + "  0::bigint AS employees, "
                    + "  (SELECT COUNT(*) FROM users WHERE deleted_at IS NULL) AS users, "
                    + "  0::bigint AS departments, "
                    + "  (SELECT COUNT(*) FROM roles WHERE deleted_at IS NULL) AS roles";

    @PersistenceContext private EntityManager em;

    /**
     * Returns the four counters as a {@code {employees, users, departments, roles}} tuple in one
     * round trip.
     */
    public long[] loadCounts() {
        Object[] row = (Object[]) em.createNativeQuery(COUNTS_SQL).getSingleResult();
        return new long[] {
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(),
            ((Number) row[3]).longValue()
        };
    }
}
