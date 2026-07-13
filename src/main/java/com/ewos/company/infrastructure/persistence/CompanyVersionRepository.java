package com.ewos.company.infrastructure.persistence;

import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyVersionRepository extends JpaRepository<CompanyVersion, UUID> {

    /** Version whose window is currently open (no effective_to). */
    Optional<CompanyVersion> findByCompanyAndEffectiveToIsNull(Company company);

    /** All versions for a company, newest effective date first. */
    List<CompanyVersion> findByCompanyOrderByEffectiveFromDesc(Company company);

    /** Version effective as of a given date, if any. */
    @Query(
            "SELECT v FROM CompanyVersion v WHERE v.company = :company"
                    + " AND v.effectiveFrom <= :date"
                    + " AND (v.effectiveTo IS NULL OR v.effectiveTo >= :date)")
    Optional<CompanyVersion> findEffectiveAt(
            @Param("company") Company company, @Param("date") LocalDate date);
}
