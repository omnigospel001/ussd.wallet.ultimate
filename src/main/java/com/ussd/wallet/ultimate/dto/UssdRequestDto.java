package com.ussd.wallet.ultimate.dto;

import lombok.Data;

@Data
public class UssdRequestDto {
    private String sessionId;
    private String msisdn;
    private String text;
}
