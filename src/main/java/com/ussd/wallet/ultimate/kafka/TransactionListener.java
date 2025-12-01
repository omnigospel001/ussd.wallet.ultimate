package com.ussd.wallet.ultimate.kafka;

import akka.actor.typed.ActorSystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussd.wallet.ultimate.domain.Transaction;
import com.ussd.wallet.ultimate.repository.TransactionCassandraRepository;
import com.ussd.wallet.ultimate.saga.SagaCoordinatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final TransactionCassandraRepository cassandraRepo;
    private final SagaCoordinatorService sagaCoordinator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransactionListener(TransactionCassandraRepository cassandraRepo, SagaCoordinatorService sagaCoordinator) {
        this.cassandraRepo = cassandraRepo;
        this.sagaCoordinator = sagaCoordinator;
    }

    @KafkaListener(topics = "transactions", groupId = "ussd-wallet-group")
    public void onMessage(String payload) {
        try {
            Transaction t = objectMapper.readValue(payload, Transaction.class);
            // persist to cassandra
            cassandraRepo.save(t);
            log.info("Persisted transaction {} to Cassandra", t.getId());
            // if transaction is withdraw and pending -> start saga
            if ("WITHDRAW".equalsIgnoreCase(t.getType())) {
                log.info("Starting saga for withdraw tx={}", t.getId());
                sagaCoordinator.startWithdrawalSaga(t);
            }
        } catch (Exception e) {
            log.error("Error processing transaction message: {}", e.getMessage(), e);
        }
    }
}

