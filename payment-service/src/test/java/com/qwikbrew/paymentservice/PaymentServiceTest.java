package com.qwikbrew.paymentservice;

import com.qwikbrew.paymentservice.dto.*;
import com.qwikbrew.paymentservice.model.WalletBalance;
import com.qwikbrew.paymentservice.model.WalletTransaction;
import com.qwikbrew.paymentservice.repository.WalletBalanceRepository;
import com.qwikbrew.paymentservice.repository.WalletTransactionRepository;
import com.qwikbrew.paymentservice.service.PaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock WalletBalanceRepository     balanceRepo;
    @Mock WalletTransactionRepository txRepo;
    @InjectMocks PaymentService       paymentService;

    private WalletBalance richWallet;
    private WalletBalance emptyWallet;

    @BeforeEach
    void setUp() {
        richWallet = WalletBalance.builder()
            .userId("user-001")
            .balance(new BigDecimal("1000.00"))
            .build();

        emptyWallet = WalletBalance.builder()
            .userId("user-002")
            .balance(BigDecimal.ZERO)
            .build();
    }

    // ── charge — wallet ───────────────────────────────────────────────────────

    @Test @DisplayName("charge WALLET — deducts amount from wallet successfully")
    void chargeWallet_sufficientBalance_deductsAndReturnsSuccess() {
        ChargeRequest req = new ChargeRequest(
            "user-001", new BigDecimal("190.00"),
            WalletTransaction.PayMethod.WALLET, "order-001");

        when(balanceRepo.findById("user-001")).thenReturn(Optional.of(richWallet));
        when(balanceRepo.save(any())).thenReturn(richWallet);
        when(txRepo.save(any())).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            tx.setId("tx-001");
            return tx;
        });

        ChargeResponse result = paymentService.charge(req);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getTransactionId()).isNotBlank();
        assertThat(result.getNewBalance()).isEqualByComparingTo("810.00");
        verify(balanceRepo).save(argThat(wb ->
            wb.getBalance().compareTo(new BigDecimal("810.00")) == 0));
    }

    @Test @DisplayName("charge WALLET — insufficient balance throws IllegalStateException")
    void chargeWallet_insufficientBalance_throwsException() {
        ChargeRequest req = new ChargeRequest(
            "user-002", new BigDecimal("500.00"),
            WalletTransaction.PayMethod.WALLET, "order-bad");

        when(balanceRepo.findById("user-002")).thenReturn(Optional.of(emptyWallet));

        assertThatThrownBy(() -> paymentService.charge(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient wallet balance");
        verify(balanceRepo, never()).save(any());
    }

    @Test @DisplayName("charge WALLET — wallet not found throws IllegalStateException")
    void chargeWallet_noWalletFound_throws() {
        ChargeRequest req = new ChargeRequest(
            "ghost-user", new BigDecimal("100"),
            WalletTransaction.PayMethod.WALLET, "order-x");

        when(balanceRepo.findById("ghost-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.charge(req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Wallet not found");
    }

    @Test @DisplayName("charge WALLET — exact balance amount succeeds")
    void chargeWallet_exactBalance_succeeds() {
        ChargeRequest req = new ChargeRequest(
            "user-001", new BigDecimal("1000.00"),
            WalletTransaction.PayMethod.WALLET, "order-exact");

        when(balanceRepo.findById("user-001")).thenReturn(Optional.of(richWallet));
        when(balanceRepo.save(any())).thenReturn(richWallet);
        when(txRepo.save(any())).thenAnswer(inv -> { ((WalletTransaction)inv.getArgument(0)).setId("tx-x"); return inv.getArgument(0); });

        ChargeResponse result = paymentService.charge(req);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getNewBalance()).isEqualByComparingTo("0.00");
    }

    // ── charge — gateway ──────────────────────────────────────────────────────

    @Test @DisplayName("charge UPI — routes to gateway, no balance deducted")
    void chargeUpi_routesToGateway_noBalanceChange() {
        ChargeRequest req = new ChargeRequest(
            "user-001", new BigDecimal("250.00"),
            WalletTransaction.PayMethod.UPI, "order-002");

        when(txRepo.save(any())).thenAnswer(inv -> {
            WalletTransaction tx = inv.getArgument(0);
            tx.setId("gw-tx-001");
            return tx;
        });

        ChargeResponse result = paymentService.charge(req);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getNewBalance()).isNull(); // gateway charges don't return balance
        verify(balanceRepo, never()).findById(any());
    }

    @Test @DisplayName("charge CARD — routes to gateway successfully")
    void chargeCard_routesToGateway() {
        ChargeRequest req = new ChargeRequest(
            "user-001", new BigDecimal("400.00"),
            WalletTransaction.PayMethod.CARD, "order-003");

        when(txRepo.save(any())).thenAnswer(inv -> { ((WalletTransaction)inv.getArgument(0)).setId("gw-card"); return inv.getArgument(0); });

        ChargeResponse result = paymentService.charge(req);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    // ── refund ────────────────────────────────────────────────────────────────

    @Test @DisplayName("refund — credits amount back to existing wallet")
    void refund_existingWallet_creditsBalance() {
        RefundRequest req = new RefundRequest("user-001", new BigDecimal("190.00"), "order-001");

        when(balanceRepo.findById("user-001")).thenReturn(Optional.of(richWallet));
        when(balanceRepo.save(any())).thenReturn(richWallet);
        when(txRepo.save(any())).thenAnswer(inv -> { ((WalletTransaction)inv.getArgument(0)).setId("ref-001"); return inv.getArgument(0); });

        RefundResponse result = paymentService.refund(req);

        assertThat(result.getTransactionId()).isNotBlank();
        assertThat(result.getNewBalance()).isEqualByComparingTo("1190.00");
        verify(balanceRepo).save(argThat(wb ->
            wb.getBalance().compareTo(new BigDecimal("1190.00")) == 0));
    }

    @Test @DisplayName("refund — creates new wallet for user with no prior balance")
    void refund_noWallet_createsNewWalletWithRefundAmount() {
        RefundRequest req = new RefundRequest("new-user", new BigDecimal("80.00"), "order-x");

        when(balanceRepo.findById("new-user")).thenReturn(Optional.empty());
        when(balanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> { ((WalletTransaction)inv.getArgument(0)).setId("ref-new"); return inv.getArgument(0); });

        RefundResponse result = paymentService.refund(req);

        assertThat(result.getNewBalance()).isEqualByComparingTo("80.00");
    }

    // ── topUp ─────────────────────────────────────────────────────────────────

    @Test @DisplayName("topUp — credits amount to existing wallet")
    void topUp_existingWallet_increasesBalance() {
        TopUpRequest req = new TopUpRequest(
            "user-001", new BigDecimal("500.00"), WalletTransaction.PayMethod.UPI);

        when(balanceRepo.findById("user-001")).thenReturn(Optional.of(richWallet));
        when(balanceRepo.save(any())).thenReturn(richWallet);
        when(txRepo.save(any())).thenReturn(new WalletTransaction());

        WalletResponse result = paymentService.topUp(req);

        assertThat(result.getUserId()).isEqualTo("user-001");
        assertThat(result.getBalance()).isEqualByComparingTo("1500.00");
    }

    @Test @DisplayName("topUp — creates wallet for first-time user")
    void topUp_noExistingWallet_createsWallet() {
        TopUpRequest req = new TopUpRequest(
            "new-user", new BigDecimal("200.00"), WalletTransaction.PayMethod.CARD);

        when(balanceRepo.findById("new-user")).thenReturn(Optional.empty());
        when(balanceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenReturn(new WalletTransaction());

        WalletResponse result = paymentService.topUp(req);

        assertThat(result.getBalance()).isEqualByComparingTo("200.00");
    }

    // ── getBalance ────────────────────────────────────────────────────────────

    @Test @DisplayName("getBalance — returns correct balance for existing wallet")
    void getBalance_existingWallet_returnsBalance() {
        when(balanceRepo.findById("user-001")).thenReturn(Optional.of(richWallet));
        WalletResponse result = paymentService.getBalance("user-001");
        assertThat(result.getUserId()).isEqualTo("user-001");
        assertThat(result.getBalance()).isEqualByComparingTo("1000.00");
    }

    @Test @DisplayName("getBalance — returns ZERO for user with no wallet")
    void getBalance_noWallet_returnsZero() {
        when(balanceRepo.findById("unknown")).thenReturn(Optional.empty());
        WalletResponse result = paymentService.getBalance("unknown");
        assertThat(result.getBalance()).isEqualByComparingTo("0.00");
    }

    // ── getTransactions ───────────────────────────────────────────────────────

    @Test @DisplayName("getTransactions — returns list ordered by createdAt desc")
    void getTransactions_returnsListForUser() {
        WalletTransaction t1 = new WalletTransaction();
        WalletTransaction t2 = new WalletTransaction();
        when(txRepo.findByUserIdOrderByCreatedAtDesc("user-001"))
            .thenReturn(List.of(t1, t2));
        List<WalletTransaction> result = paymentService.getTransactions("user-001");
        assertThat(result).hasSize(2);
    }

    @Test @DisplayName("getTransactions — returns empty list for user with no transactions")
    void getTransactions_noTransactions_returnsEmpty() {
        when(txRepo.findByUserIdOrderByCreatedAtDesc("new-user"))
            .thenReturn(Collections.emptyList());
        assertThat(paymentService.getTransactions("new-user")).isEmpty();
    }
}
