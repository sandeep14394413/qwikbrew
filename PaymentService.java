// PaymentService.java

public class PaymentService {
    private String paymentId;
    private Double amount;
    private String currency;
    private PaymentStatus status;

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    // Builder pattern implementation
    public static class Builder {
        private PaymentService paymentService;

        public Builder() {
            paymentService = new PaymentService();
        }

        public Builder withPaymentId(String paymentId) {
            paymentService.setPaymentId(paymentId);
            return this;
        }

        public Builder withAmount(Double amount) {
            paymentService.setAmount(amount);
            return this;
        }

        public Builder withCurrency(String currency) {
            paymentService.setCurrency(currency);
            return this;
        }

        public Builder withStatus(PaymentStatus status) {
            paymentService.setStatus(status);
            return this;
        }

        public PaymentService build() {
            return paymentService;
        }
    }
}