package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.PayrollCollaboration;
import com.ewos.tenancy.domain.PayrollCollaborationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollCollaborationRepository extends JpaRepository<PayrollCollaboration, UUID> {

    List<PayrollCollaboration> findAllByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<PayrollCollaboration> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);

    List<PayrollCollaboration> findAllByProviderIdAndStatus(
            UUID providerId, PayrollCollaborationStatus status);

    boolean existsByClientIdAndProviderIdAndStatus(
            UUID clientId, UUID providerId, PayrollCollaborationStatus status);
}
