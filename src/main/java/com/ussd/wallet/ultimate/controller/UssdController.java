package com.ussd.wallet.ultimate.controller;

import com.ussd.wallet.ultimate.dto.UssdRequestDto;
import com.ussd.wallet.ultimate.dto.UssdResponseDto;
import com.ussd.wallet.ultimate.service.UssdService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ussd")
public class UssdController {

    private final UssdService ussdService;

    public UssdController(UssdService ussdService) {
        this.ussdService = ussdService;
    }

    @PostMapping
    public ResponseEntity<UssdResponseDto> receive(@RequestBody UssdRequestDto req) {
        UssdResponseDto res = ussdService.handle(req);
        return ResponseEntity.ok(res);
    }
}

