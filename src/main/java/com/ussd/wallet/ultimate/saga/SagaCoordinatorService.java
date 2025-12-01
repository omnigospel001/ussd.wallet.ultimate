package com.ussd.wallet.ultimate.saga;

import akka.actor.typed.ActorSystem;

import akka.actor.typed.Props;
import com.ussd.wallet.ultimate.domain.Transaction;
import com.ussd.wallet.ultimate.repository.TransactionCassandraRepository;
import com.ussd.wallet.ultimate.service.AccountService;
import com.ussd.wallet.ultimate.service.FlutterwavePaymentService;
import com.ussd.wallet.ultimate.service.TwilioSmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class SagaCoordinatorService {

    private static final Logger log = LoggerFactory.getLogger(SagaCoordinatorService.class);

    private final ActorSystem<Void> actorSystem;
    private final FlutterwavePaymentService paymentService;
    private final AccountService accountService;
    private final TwilioSmsService smsService;
    private final TransactionCassandraRepository cassandraRepo;

    public SagaCoordinatorService(ActorSystem<Void> actorSystem,
                                  FlutterwavePaymentService paymentService,
                                  AccountService accountService,
                                  TwilioSmsService smsService,
                                  TransactionCassandraRepository cassandraRepo) {
        this.actorSystem = actorSystem;
        this.paymentService = paymentService;
        this.accountService = accountService;
        this.smsService = smsService;
        this.cassandraRepo = cassandraRepo;
    }

    public void startWithdrawalSaga(Transaction transaction) {
        // spawn a WithdrawalSaga actor per transaction
        var name = "withdraw-saga-" + transaction.getId().toString();
        var behavior = WithdrawalSaga.create(transaction, paymentService, accountService, smsService, cassandraRepo);
        try {
            //actorSystem.spawn(behavior, name);
            actorSystem.systemActorOf(behavior, name, Props.empty());
            log.info("Spawned saga actor {}", name);
        } catch (Exception e) {
            log.error("Failed to spawn saga actor: {}", e.getMessage(), e);
        }
    }
}

