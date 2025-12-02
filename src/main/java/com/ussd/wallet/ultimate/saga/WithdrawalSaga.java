package com.ussd.wallet.ultimate.saga;



import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;

import com.ussd.wallet.ultimate.domain.Transaction;
import com.ussd.wallet.ultimate.repository.TransactionCassandraRepository;
import com.ussd.wallet.ultimate.service.AccountService;
import com.ussd.wallet.ultimate.service.FlutterwavePaymentService;
import com.ussd.wallet.ultimate.service.TwilioSmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

public class WithdrawalSaga extends AbstractBehavior<WithdrawalSaga.Command> {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalSaga.class);

    public interface Command {}

    public record Start() implements Command {}
    public record PaymentResult(boolean success, String providerRef, String error) implements Command {}
    public record Retry() implements Command {}

    private final Transaction tx;
    private final FlutterwavePaymentService paymentService;
    private final AccountService accountService;
    private final TwilioSmsService smsService;
    private final TransactionCassandraRepository cassandraRepo;
    private int attempts = 0;
    private final int maxAttempts = 3;

    public static Behavior<Command> create(Transaction tx,
                                           FlutterwavePaymentService paymentService,
                                           AccountService accountService,
                                           TwilioSmsService smsService,
                                           TransactionCassandraRepository cassandraRepo) {
        return Behaviors.setup(ctx -> new WithdrawalSaga(ctx, tx, paymentService, accountService, smsService, cassandraRepo));
    }

    private WithdrawalSaga(ActorContext<Command> context,
                           Transaction tx,
                           FlutterwavePaymentService paymentService,
                           AccountService accountService,
                           TwilioSmsService smsService,
                           TransactionCassandraRepository cassandraRepo) {
        super(context);
        this.tx = tx;
        this.paymentService = paymentService;
        this.accountService = accountService;
        this.smsService = smsService;
        this.cassandraRepo = cassandraRepo;

        // start immediately
        context.getSelf().tell(new Start());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Start.class, this::onStart)
                .onMessage(PaymentResult.class, this::onPaymentResult)
                .onMessage(Retry.class, this::onRetry)
                .build();
    }

    private Behavior<Command> onStart(Start msg) {
        attempts = 1;
        doPayment();
        return this;
    }

    private Behavior<Command> onRetry(Retry msg) {
        attempts++;
        log.info("Retrying payment for tx={} attempt={}", tx.getId(), attempts);
        doPayment();
        return this;
    }

    private Behavior<Command> onPaymentResult(PaymentResult res) {
        if (res.success) {
            log.info("Payment succeeded for tx={} providerRef={}", tx.getId(), res.providerRef);
            // update transaction status in cassandra
            tx.setStatus("SUCCESS");
            tx.setMeta(res.providerRef);
            cassandraRepo.save(tx);
            smsService.sendSms(findMsisdnForAccount(tx.getAccountId()), "Withdrawal successful: " + tx.getAmount() + " " + tx.getCurrency());
            return Behaviors.stopped();
        } else {
            log.warn("Payment failed for tx={} error={}", tx.getId(), res.error);
            if (attempts < maxAttempts) {
                // schedule retry with exponential backoff
                long backoffMillis = (long) Math.pow(2, attempts) * 1000;
                getContext().scheduleOnce(Duration.ofMillis(backoffMillis), getContext().getSelf(), new Retry());
                return this;
            } else {
                // permanent failure: compensate (credit back)
                try {
                    accountService.compensateCredit(tx.getAccountId(), BigDecimal.valueOf(tx.getAmount().doubleValue()), tx.getCurrency(), res.error);
                    tx.setStatus("FAILED"); // mark failed
                    cassandraRepo.save(tx);
                    smsService.sendSms(findMsisdnForAccount(tx.getAccountId()), "Withdrawal failed and funds have been returned: " + tx.getAmount() + " " + tx.getCurrency());
                } catch (Exception e) {
                    log.error("Compensation failed for tx={}: {}", tx.getId(), e.getMessage(), e);
                }
                return Behaviors.stopped();
            }
        }
    }

    private void doPayment() {
        // run payment in a separate thread and pipe result back
        getContext().getExecutionContext().execute(() -> {
            try {
                // For demo we call paymentService with placeholder account details.
                var resp = paymentService.initiateTransfer("25436866857", "000", tx.getCurrency(), tx.getAmount().toPlainString(), "USSD withdrawal " + tx.getId());
                // interpret response map for success (depends on provider)
                boolean success = resp != null && (resp.getOrDefault("status", "success").toString().equalsIgnoreCase("success") || resp.getOrDefault("status", "ok").toString().equalsIgnoreCase("ok"));
                String providerRef = resp != null && resp.containsKey("data") ? resp.get("data").toString() : resp != null && resp.containsKey("reference") ? resp.get("reference").toString() : "";
                getContext().getSelf().tell(new PaymentResult(success, providerRef, success ? null : "provider_error"));
            } catch (Exception e) {
                getContext().getSelf().tell(new PaymentResult(false, null, e.getMessage()));
            }
        });
    }

    private String findMsisdnForAccount(Long accountId) {
        return "+2348164509876";
    }
}

