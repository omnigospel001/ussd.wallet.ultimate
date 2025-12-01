package com.ussd.wallet.ultimate.service;


import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class TwilioSmsService {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsService.class);

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    @PostConstruct
    public void init() {
        if (accountSid != null && !accountSid.isEmpty() && authToken != null && !authToken.isEmpty()) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("Twilio initialized");
            } catch (Exception e) {
                log.error("Twilio init failed: {}", e.getMessage(), e);
            }
        } else {
            log.warn("Twilio credentials not provided; SMS will be logged");
        }
    }

    public void sendSms(String to, String message) {
        try {
            if (accountSid == null || accountSid.isEmpty() || authToken == null || authToken.isEmpty() || fromNumber == null || fromNumber.isEmpty()) {
                log.info("[SMS-MOCK] to={} msg={}", to, message);
                return;
            }
            Message.creator(new com.twilio.type.PhoneNumber(to), new com.twilio.type.PhoneNumber(fromNumber), message).create();
            log.info("Sent SMS to {}", to);
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", to, e.getMessage(), e);
        }
    }
}

