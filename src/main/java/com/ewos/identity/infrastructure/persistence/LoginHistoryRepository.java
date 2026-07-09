package com.ewos.identity.infrastructure.persistence;

import com.ewos.identity.domain.LoginHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, UUID> {}
