package com.diezaster.ledger.service;

import com.diezaster.ledger.domain.Account;
import com.diezaster.ledger.domain.LedgerEntry;
import com.diezaster.ledger.domain.OutboxEvent;
import com.diezaster.ledger.repository.AccountRepository;
import com.diezaster.ledger.repository.LedgerEntryRepository;
import com.diezaster.ledger.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class LedgerTransferService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;

    public LedgerTransferService(AccountRepository accountRepository,
                                 LedgerEntryRepository ledgerEntryRepository,
                                 OutboxEventRepository outboxEventRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Executes double-entry transfer with deterministic lock ordering to guarantee zero deadlocks.
     */
    @Transactional
    public UUID transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        // DETERMINISTIC LOCK ORDERING:
        // Always lock account with lexicographically smaller UUID first.
        UUID firstLockId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        UUID secondLockId = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

        Account firstAccount = accountRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + firstLockId));
        Account secondAccount = accountRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + secondLockId));

        Account fromAccount = fromAccountId.equals(firstLockId) ? firstAccount : secondAccount;
        Account toAccount = toAccountId.equals(firstLockId) ? firstAccount : secondAccount;

        // Perform balance operations
        fromAccount.debit(amount);
        toAccount.credit(amount);

        UUID transactionId = UUID.randomUUID();

        // STRICT DOUBLE-ENTRY BOOKKEEPING INVARIANT:
        // Sum(Debit) == Sum(Credit) == amount
        LedgerEntry debitEntry = new LedgerEntry(transactionId, fromAccountId, LedgerEntry.EntryType.DEBIT, amount);
        LedgerEntry creditEntry = new LedgerEntry(transactionId, toAccountId, LedgerEntry.EntryType.CREDIT, amount);

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // TRANSACTIONAL OUTBOX: Atomically record event in database within the same ACID transaction
        String eventPayload = String.format("{\"transactionId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%s}",
                transactionId, fromAccountId, toAccountId, amount);
        OutboxEvent event = new OutboxEvent("TRANSFER", transactionId, "FUNDS_TRANSFERRED", eventPayload);
        outboxEventRepository.save(event);

        return transactionId;
    }
}
