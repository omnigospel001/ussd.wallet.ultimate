package com.ussd.wallet.ultimate.util;

import java.util.UUID;

public class IdempotencyKeyUtil {
    public static String newKey() {
        return UUID.randomUUID().toString();
    }
}

