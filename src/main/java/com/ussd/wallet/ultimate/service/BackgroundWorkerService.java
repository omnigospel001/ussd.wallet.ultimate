package com.ussd.wallet.ultimate.service;

import com.ussd.wallet.ultimate.domain.Transaction;
import com.ussd.wallet.ultimate.repository.TransactionCassandraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BackgroundWorkerService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundWorkerService.class);
    private final TransactionCassandraRepository cassandraRepo;

    public BackgroundWorkerService(TransactionCassandraRepository cassandraRepo) {
        this.cassandraRepo = cassandraRepo;
    }

    @Async
    public void publishTransaction(Transaction transaction) {
        try {
            cassandraRepo.save(transaction);
            log.info("Persisted transaction {} to Cassandra", transaction.getId());
        } catch (Exception e) {
            log.error("Failed to persist transaction to Cassandra: {}", e.getMessage(), e);
        }
    }
}

