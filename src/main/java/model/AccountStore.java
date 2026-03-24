// model/AccountStore.java
package model;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountStore {

    // The store. Key = account number, Value = Account object.
    private final Map<Integer, Account> accounts = new HashMap<>();

    // Auto-incrementing account number. AtomicInteger is thread-safe
    // so two simultaneous openAccount() calls never get the same number.
    private final AtomicInteger nextId = new AtomicInteger(1001);

    // openAccount — creates a new account, returns its assigned number.
    // synchronized means only one thread can be inside this method at a time.
    public synchronized int openAccount(String name, String password,
                                        Currency currency, float initialBalance) {
        int no = nextId.getAndIncrement();
        accounts.put(no, new Account(no, name, password, currency, initialBalance));
        return no;
    }

    // closeAccount — removes the account after verifying credentials.
    // Returns null on success, or an error string describing the problem.
    // Returning a string instead of throwing makes the handler code cleaner.
    public synchronized String closeAccount(int no, String name, String password) {
        Account acc = accounts.get(no);
        if (acc == null)                     return "Account not found.";
        if (!acc.holderName.equals(name))    return "Name does not match.";
        if (!acc.password.equals(password))  return "Incorrect password.";
        accounts.remove(no);
        return null;   // null means success
    }

    /**
     *
     * @param no Account number
     * @param currency Currency must match the account's currency, otherwise it's an error.
     * @param amount Amount to deposit (positive) or withdraw (negative)
     * @return Account Balance
     */
    public synchronized float depositWithdraw(int no, String name, String password,
                                              Currency currency, float amount) {
        Account acc = accounts.get(no);
        if (acc == null)                     throw new IllegalArgumentException("Account not found.");
        if (!acc.holderName.equals(name))    throw new IllegalArgumentException("Name mismatch.");
        if (!acc.password.equals(password))  throw new IllegalArgumentException("Wrong password.");
        if (acc.currency != currency)        throw new IllegalArgumentException("Currency mismatch.");
        if (amount < 0 && acc.balance + amount < 0)
            throw new IllegalArgumentException("Insufficient balance.");
        acc.balance += amount;
        return acc.balance;
    }

    // queryBalance — read-only, no side effects. Returns null on bad credentials.
    public synchronized Float queryBalance(int no, String name, String password) {
        Account acc = accounts.get(no);
        if (acc == null || !acc.holderName.equals(name) || !acc.password.equals(password))
            return null;
        return acc.balance;
    }

    // transfer — debits src, credits dst atomically (both in same synchronized block).
    // Returns null on success, error string on failure.
    public synchronized String transfer(int srcNo, int dstNo, String name,
                                        String password, float amount) {
        Account src = accounts.get(srcNo);
        Account dst = accounts.get(dstNo);
        if (src == null)                     return "Source account not found.";
        if (dst == null)                     return "Destination account not found.";
        if (!src.holderName.equals(name))    return "Name mismatch on source.";
        if (!src.password.equals(password))  return "Wrong password.";
        if (src.currency != dst.currency)    return "Currency mismatch.";
        if (src.balance < amount)            return "Insufficient balance.";
        src.balance -= amount;
        dst.balance += amount;
        return null;
    }

    public synchronized Account get(int no) { return accounts.get(no); }
}