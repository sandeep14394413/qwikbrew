package com.qwikbrew.paymentservice.repository;

import com.qwikbrew.paymentservice.model.WalletBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletBalanceRepository extends JpaRepository<WalletBalance, String> {}
