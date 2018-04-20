package com.edi.learn.axon.common.events;

import com.edi.learn.axon.common.domain.AccountId;


public class AccountCreatedEvent {
    private AccountId accountId;
    private String accountName;
    private long amount;

    public AccountCreatedEvent(AccountId accountId, String accountName, long amount) {
        this.accountId = accountId;
        this.accountName = accountName;
        this.amount = amount;
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public long getAmount() {
        return amount;
    }
}
