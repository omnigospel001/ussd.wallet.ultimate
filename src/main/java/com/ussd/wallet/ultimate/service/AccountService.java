package com.ussd.wallet.ultimate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ussd.wallet.ultimate.domain.Account;
import com.ussd.wallet.ultimate.domain.Transaction;
import com.ussd.wallet.ultimate.repository.AccountRepository;
import com.ussd.wallet.ultimate.repository.TransactionCassandraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private final AccountRepository accountRepository;
    private final TransactionCassandraRepository cassandraRepo;
    private final BackgroundWorkerService backgroundWorkerService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TwilioSmsService smsService;
    private final FlutterwavePaymentService paymentProviderService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountService(AccountRepository accountRepository, TransactionCassandraRepository cassandraRepo,
                          BackgroundWorkerService backgroundWorkerService, RedisTemplate<String, Object> redisTemplate,
                          TwilioSmsService smsService, FlutterwavePaymentService paymentProviderService,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.accountRepository = accountRepository;
        this.cassandraRepo = cassandraRepo;
        this.backgroundWorkerService = backgroundWorkerService;
        this.redisTemplate = redisTemplate;
        this.smsService = smsService;
        this.paymentProviderService = paymentProviderService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public Account createAccount(Long userId, String currency) {
        Account account = Account.builder().userId(userId).currency(currency).balance(BigDecimal.ZERO).build();
        return accountRepository.save(account);
    }

    @Transactional
    public void deposit(Long accountId, BigDecimal amount, String currency, String idempotencyKey, String msisdn) {
        String key = "idem:deposit:" + idempotencyKey;
        if (redisTemplate.hasKey(key)) {
            log.info("Skipping duplicate deposit, idempotencyKey={}", idempotencyKey);
            return;
        }
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(300));

        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .type("DEPOSIT")
                .status("SUCCESS")
                .build();

        var transactionSaved = cassandraRepo.save(transaction);

        try {
            String payload = objectMapper.writeValueAsString(transactionSaved);
            kafkaTemplate.send("transactions", transactionSaved.getId().toString(), payload);
            log.info("Published deposit event to Kafka tx={}", transactionSaved.getId());
        } catch (Exception e) {
            log.error("Failed to publish deposit to Kafka: {}", e.getMessage(), e);
            backgroundWorkerService.publishTransaction(transactionSaved);
        }

        smsService.sendSms(msisdn, "Deposit successful: " + amount + " " + currency);
    }

    @Transactional
    public void withdraw(Long accountId, BigDecimal amount, String currency, String idempotencyKey, String msisdn) {
        String key = "idem:withdraw:" + idempotencyKey;
        if (redisTemplate.hasKey(key)) {
            log.info("Skipping duplicate withdraw, idempotencyKey={}", idempotencyKey);
            return;
        }
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(300));

        Account a = accountRepository.findById(accountId).orElseThrow();
        if (a.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds");
        }
        a.setBalance(a.getBalance().subtract(amount));
        accountRepository.save(a);

        Transaction t = Transaction.builder()
                .id(UUID.randomUUID())
                .accountId(accountId)
                .amount(amount)
                .currency(currency)
                .type("WITHDRAW")
                .status("PENDING")
                .build();

        try {
            String payload = objectMapper.writeValueAsString(t);
            kafkaTemplate.send("transactions", t.getId().toString(), payload);
            log.info("Published withdraw event to Kafka tx={}", t.getId());
        } catch (Exception e) {
            log.error("Failed to publish withdraw to Kafka: {}", e.getMessage(), e);
            backgroundWorkerService.publishTransaction(t);
        }

        smsService.sendSms(msisdn, "Withdrawal initiated: " + amount + " " + currency);
    }

    public Optional<Account> findAccountByUserId(Long userId, String currency) {
        return accountRepository.findByUserIdAndCurrency(userId, currency);
    }

    public BigDecimal getBalanceForUser(Long userId, String currency) {
        Optional<Account> opt = accountRepository.findByUserIdAndCurrency(userId, currency);
        return opt.map(Account::getBalance).orElse(BigDecimal.ZERO);
    }

    // Compensation: credit back funds on permanent failure
    @Transactional
    public void compensateCredit(Long accountId, BigDecimal amount, String currency, String reason) {
        Account a = accountRepository.findById(accountId).orElseThrow();
        a.setBalance(a.getBalance().add(amount));
        accountRepository.save(a);
        log.warn("Compensated account {} with {} {} due to {}", accountId, amount, currency, reason);
    }
}

