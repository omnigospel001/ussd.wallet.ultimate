package com.ussd.wallet.ultimate.repository;

import com.ussd.wallet.ultimate.domain.Transaction;
import org.springframework.data.cassandra.repository.CassandraRepository;
import java.util.UUID;

public interface TransactionCassandraRepository extends CassandraRepository<Transaction, UUID> {
}
