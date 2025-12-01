package com.ussd.wallet.ultimate.controller;

import com.ussd.wallet.ultimate.service.AccountService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final Counter depositCounter;

    public AccountController(AccountService accountService, MeterRegistry registry) {
        this.accountService = accountService;
        this.depositCounter = Counter.builder("ussd.wallet.deposit.count").description("Number of deposits").register(registry);
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, String> body) {
        Long accountId = Long.parseLong(body.get("accountId"));
        BigDecimal amount = new BigDecimal(body.get("amount"));
        String currency = body.getOrDefault("currency", "NGN");
        String idempotencyKey = body.getOrDefault("idempotencyKey", java.util.UUID.randomUUID().toString());
        String msisdn = body.getOrDefault("msisdn", "unknown");
        accountService.deposit(accountId, amount, currency, idempotencyKey, msisdn);
        depositCounter.increment();
        return ResponseEntity.ok(Map.of("status","ok","idempotencyKey", idempotencyKey));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, String> body) {
        Long accountId = Long.parseLong(body.get("accountId"));
        BigDecimal amount = new BigDecimal(body.get("amount"));
        String currency = body.getOrDefault("currency", "NGN");
        String idempotencyKey = body.getOrDefault("idempotencyKey", java.util.UUID.randomUUID().toString());
        String msisdn = body.getOrDefault("msisdn", "unknown");
        accountService.withdraw(accountId, amount, currency, idempotencyKey, msisdn);
        return ResponseEntity.ok(Map.of("status","ok","idempotencyKey", idempotencyKey));
    }
}

