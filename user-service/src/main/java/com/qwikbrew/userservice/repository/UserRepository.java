package com.qwikbrew.userservice.repository;

import com.qwikbrew.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.walletBalance = u.walletBalance + :amount WHERE u.id = :userId")
    int creditWallet(String userId, BigDecimal amount);

    @Modifying
    @Query("UPDATE User u SET u.walletBalance = u.walletBalance - :amount WHERE u.id = :userId AND u.walletBalance >= :amount")
    int debitWallet(String userId, BigDecimal amount);

    @Modifying
    @Query("UPDATE User u SET u.brewPoints = u.brewPoints + :points WHERE u.id = :userId")
    int addBrewPoints(String userId, int points);
}
