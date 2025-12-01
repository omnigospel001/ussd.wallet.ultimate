package com.ussd.wallet.ultimate.service;

import com.ussd.wallet.ultimate.domain.User;
import com.ussd.wallet.ultimate.dto.UssdRequestDto;
import com.ussd.wallet.ultimate.dto.UssdResponseDto;
import com.ussd.wallet.ultimate.repository.UserRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class UssdService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final TwilioSmsService smsService;

    public UssdService(RedisTemplate<String, Object> redisTemplate, UserRepository userRepository, AccountService accountService, PasswordEncoder passwordEncoder, TwilioSmsService smsService) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
    }

    public UssdResponseDto handle(UssdRequestDto req) {
        String sessionKey = "session:" + req.getSessionId();
        Object stateObj = redisTemplate.opsForValue().get(sessionKey);
        String state = stateObj == null ? "MENU" : (String) stateObj;

        String incoming = req.getText() == null ? "" : req.getText().trim();
        // USSD gateway often sends full text like '1*1234', we'll split by *
        String[] parts = incoming.split("\\*") ;

        // Determine last user input
        String last = parts.length == 0 ? "" : parts[parts.length - 1];

        // Simple menu
        if (state.equals("MENU") && (incoming.isEmpty() || incoming.equals(""))) {
            redisTemplate.opsForValue().set(sessionKey, "MENU.SELECT", Duration.ofSeconds(120));
            String menu = "CON Welcome to USSD Wallet\n1. Create Account\n2. Deposit\n3. Withdraw\n4. Check Balance";
            return new UssdResponseDto(menu, false);
        }

        // Handle menu selection based on the first input (parts[0])
        String first = parts.length > 0 ? parts[0] : "";

        try {
            switch (first) {
                case "1": // Create account flow
                    return handleCreateFlow(req, sessionKey, parts);
                case "3": // Withdraw flow (requires PIN)
                    return handleWithdrawFlow(req, sessionKey, parts);
                case "4":
                    return handleCheckBalance(req);
                default:
                    return new UssdResponseDto("END Unknown option", true);
            }
        } catch (Exception e) {
            // log and return friendly error
            e.printStackTrace();
            return new UssdResponseDto("END An error occurred. Try again later.", true);
        }
    }

    @Transactional
    protected UssdResponseDto handleCreateFlow(UssdRequestDto req, String sessionKey, String[] parts) {
        // Flow steps:
        // 1 -> ask choose PIN (4 digits)
        // 1*PIN -> ask to confirm PIN
        // 1*PIN*CONFIRM -> create account and save hashed PIN
        String msisdn = req.getMsisdn();
        Optional<User> existing = userRepository.findByMsisdn(msisdn);
        if (existing.isPresent()) {
            return new UssdResponseDto("END Account already exists for this number", true);
        }
        if (parts.length == 1) {
            // asked to choose pin
            redisTemplate.opsForValue().set(sessionKey, "CREATE.AWAIT_PIN", Duration.ofSeconds(120));
            return new UssdResponseDto("CON Please enter a 4-digit PIN for your wallet", false);
        } else if (parts.length == 2) {
            String pin = parts[1];
            if (!pin.matches("\\d{4}")) {
                return new UssdResponseDto("CON Invalid PIN. Enter a 4-digit PIN", false);
            }
            // store temp pin in redis
            redisTemplate.opsForValue().set(sessionKey + ":pin", pin, Duration.ofSeconds(120));
            return new UssdResponseDto("CON Confirm your 4-digit PIN", false);
        } else if (parts.length >= 3) {
            String confirm = parts[2];
            String saved = (String) redisTemplate.opsForValue().get(sessionKey + ":pin");
            if (saved == null) {
                return new UssdResponseDto("END Session expired. Start again", true);
            }
            if (!saved.equals(confirm)) {
                return new UssdResponseDto("END PINs do not match. Start again.", true);
            }
            // create user and account
            User user = User.builder().msisdn(msisdn).fullName("").pinHash(passwordEncoder.encode(saved)).build();
            user = userRepository.save(user);
            accountService.createAccount(user.getId(), user.getDefaultCurrency());
            // send welcome SMS
            smsService.sendSms(msisdn, "Welcome to USSD Wallet. Your account has been created.");
            // clear session
            redisTemplate.delete(sessionKey);
            redisTemplate.delete(sessionKey + ":pin");
            return new UssdResponseDto("END Account created successfully", true);
        }
        return new UssdResponseDto("END Invalid flow", true);
    }

    protected UssdResponseDto handleCheckBalance(UssdRequestDto req) {
        var user = userRepository.findByMsisdn(req.getMsisdn());
        if (user.isEmpty()) return new UssdResponseDto("END No account found", true);
        var bal = accountService.getBalanceForUser(user.get().getId(), user.get().getDefaultCurrency());
        return new UssdResponseDto("END Balance: " + bal + " " + user.get().getDefaultCurrency(), true);
    }

    protected UssdResponseDto handleWithdrawFlow(UssdRequestDto req, String sessionKey, String[] parts) {
        // Flow: 3 -> ask amount
        // 3*amount -> ask pin
        // 3*amount*pin -> process withdraw (verify pin) and initiate payout
        String msisdn = req.getMsisdn();
        var maybeUser = userRepository.findByMsisdn(msisdn);
        if (maybeUser.isEmpty()) return new UssdResponseDto("END No account found. Create one first.", true);
        var user = maybeUser.get();
        if (parts.length == 1) {
            redisTemplate.opsForValue().set(sessionKey, "WITHDRAW.AWAIT_AMOUNT", Duration.ofSeconds(120));
            return new UssdResponseDto("CON Enter amount to withdraw (e.g. 1000)", false);
        } else if (parts.length == 2) {
            String amount = parts[1];
            if (!amount.matches("\\d+")) return new UssdResponseDto("CON Invalid amount. Enter numeric amount", false);
            // store amount
            redisTemplate.opsForValue().set(sessionKey + ":amount", amount, Duration.ofSeconds(120));
            return new UssdResponseDto("CON Enter your 4-digit PIN", false);
        } else if (parts.length >= 3) {
            String pin = parts[2];
            String storedHash = user.getPinHash();
            if (storedHash == null || storedHash.isEmpty()) return new UssdResponseDto("END No PIN found. Create account again.", true);
            if (!passwordEncoder.matches(pin, storedHash)) {
                return new UssdResponseDto("END Incorrect PIN", true);
            }
            String amountStr = (String) redisTemplate.opsForValue().get(sessionKey + ":amount");
            if (amountStr == null) return new UssdResponseDto("END Session expired. Start again.", true);
            java.math.BigDecimal amount = new java.math.BigDecimal(amountStr);
            // find account id by user id (accountService will handle)
            var accOpt = accountService.findAccountByUserId(user.getId(), user.getDefaultCurrency());
            if (accOpt.isEmpty()) return new UssdResponseDto("END Account not found", true);
            var acc = accOpt.get();
            try {
                // use idempotency key based on session+timestamp
                String idem = java.util.UUID.randomUUID().toString();
                accountService.withdraw(acc.getId(), amount, acc.getCurrency(), idem, msisdn);
                // clear session keys
                redisTemplate.delete(sessionKey);
                redisTemplate.delete(sessionKey + ":amount");
                return new UssdResponseDto("END Withdrawal initiated. You will receive an SMS when complete.", true);
            } catch (IllegalArgumentException ex) {
                return new UssdResponseDto("END " + ex.getMessage(), true);
            }
        }
        return new UssdResponseDto("END Invalid flow", true);
    }
}

