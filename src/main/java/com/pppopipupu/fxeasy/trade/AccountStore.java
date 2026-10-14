package com.pppopipupu.fxeasy.trade;

import module java.base;

/// Every account of the server, keyed by owner. Accounts are created on first use.
public final class AccountStore {

    private final Map<UUID, Account> accounts = new HashMap<>();

    public Account get(UUID owner) {
        return this.accounts.computeIfAbsent(owner, Account::new);
    }

    public Collection<Account> all() {
        return Collections.unmodifiableCollection(this.accounts.values());
    }

    public void restore(Account account) {
        this.accounts.put(account.owner(), account);
    }
}
