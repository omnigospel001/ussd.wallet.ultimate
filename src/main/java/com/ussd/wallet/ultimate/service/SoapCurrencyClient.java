package com.ussd.wallet.ultimate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.ws.client.core.WebServiceTemplate;

@Service
public class SoapCurrencyClient {

    private final WebServiceTemplate ws;

    @Value("${currency.soap-wsdl}")
    private String wsdl;

    public SoapCurrencyClient(WebServiceTemplate ws) {
        this.ws = ws;
    }

    public double getRate(String from, String to) {

        if (from.equalsIgnoreCase(to)) return 1.0;
        if (from.equalsIgnoreCase("NGN") && to.equalsIgnoreCase("USD")) return 0.0024;
        if (from.equalsIgnoreCase("USD") && to.equalsIgnoreCase("NGN")) return 420.0;
        return 1.0;
    }
}

