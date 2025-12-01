package com.ussd.wallet.ultimate.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class FlutterwavePaymentService {

    private static final Logger log = LoggerFactory.getLogger(FlutterwavePaymentService.class);

    @Value("${flutterwave.base-url}")
    private String baseUrl;

    @Value("${flutterwave.secret-key}")
    private String secretKey;

    private final RestTemplate rest = new RestTemplate();

    public Map<String, Object> initiateTransfer(String accountNumber, String bankCode, String currency, String amount, String narration) {
        String url = baseUrl + "/transfers";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (secretKey != null && !secretKey.isEmpty()) headers.setBearerAuth(secretKey);

        var body = new HashMap<String, Object>();
        body.put("account_bank", bankCode);
        body.put("account_number", accountNumber);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("narration", narration);
        body.put("reference", java.util.UUID.randomUUID().toString());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> resp = rest.postForEntity(url, req, Map.class);
            log.info("Flutterwave transfer response status: {}", resp.getStatusCode());
            return resp.getBody();
        } catch (Exception e) {
            log.error("Flutterwave transfer failed: {}", e.getMessage(), e);
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}

