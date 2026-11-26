package com.anass.billing.common.charge;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChargeRepository extends JpaRepository<Charge, UUID> {

    /**
     * Loads the charge and locks its row until the surrounding transaction ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Charge c where c.id = :id")
    Optional<Charge> findByIdForUpdate(@Param("id") UUID id);
}