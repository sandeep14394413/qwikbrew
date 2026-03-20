package com.qwikbrew.paymentservice.service;

import com.qwikbrew.paymentservice.model.Payment;

public class PaymentService {

    private Payment payment;

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public void processPayment() {
        // Implementation for processing payment
    }
}