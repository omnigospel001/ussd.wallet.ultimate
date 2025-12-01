package com.ussd.wallet.ultimate.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UssdResponseDto {
    private String response;
    private boolean endSession;
}
