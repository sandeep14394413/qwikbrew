package com.qwikbrew.paymentservice.repository;

import com.qwikbrew.paymentservice.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<WalletTransaction> findTopByReferenceOrderByCreatedAtDesc(String reference);
}
