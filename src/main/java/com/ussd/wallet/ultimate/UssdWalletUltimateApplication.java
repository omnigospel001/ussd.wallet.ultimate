package com.ussd.wallet.ultimate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
public class UssdWalletUltimateApplication {

	public static void main(String[] args) {
		SpringApplication.run(UssdWalletUltimateApplication.class, args);
	}

}
