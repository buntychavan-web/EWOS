package com.ewos.identity.infrastructure.persistence;

import com.ewos.identity.domain.PasswordHistory;
import com.ewos.identity.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, UUID> {

    List<PasswordHistory> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
}
