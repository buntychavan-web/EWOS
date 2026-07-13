package com.ewos.person.infrastructure.persistence;

import com.ewos.person.domain.Person;
import com.ewos.person.domain.PersonVersion;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonVersionRepository extends JpaRepository<PersonVersion, UUID> {

    Optional<PersonVersion> findByPersonAndEffectiveToIsNull(Person person);

    List<PersonVersion> findByPersonOrderByVersionNumberDesc(Person person);

    @Query("SELECT MAX(v.versionNumber) FROM PersonVersion v WHERE v.person = :person")
    Optional<Integer> findMaxVersionNumber(@Param("person") Person person);

    @Query(
            "SELECT v FROM PersonVersion v WHERE v.person = :person "
                    + "AND v.effectiveFrom <= :asOf "
                    + "AND (v.effectiveTo IS NULL OR v.effectiveTo >= :asOf) "
                    + "ORDER BY v.effectiveFrom DESC")
    List<PersonVersion> findEffectiveAt(
            @Param("person") Person person, @Param("asOf") LocalDate asOf);
}
