package com.diezaster.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinancialLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancialLedgerApplication.class, args);
    }
}
