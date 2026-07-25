package com.ewos.dataexchange.infrastructure.persistence;

import com.ewos.dataexchange.domain.DataExchangeHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataExchangeHistoryRepository extends JpaRepository<DataExchangeHistory, UUID> {

    @Query(
            "select h from DataExchangeHistory h where h.record.id = :recordId order by"
                    + " h.occurredAt asc")
    List<DataExchangeHistory> findAllOfRecord(@Param("recordId") UUID recordId);
}
