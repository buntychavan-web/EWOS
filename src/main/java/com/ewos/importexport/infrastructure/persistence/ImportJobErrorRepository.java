package com.ewos.importexport.infrastructure.persistence;

import com.ewos.importexport.domain.ImportJobError;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobErrorRepository extends JpaRepository<ImportJobError, UUID> {

    List<ImportJobError> findAllByImportJobIdOrderByRowNumberAsc(UUID importJobId);
}
