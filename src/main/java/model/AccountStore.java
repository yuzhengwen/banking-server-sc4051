// model/AccountStore.java
package model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class AccountStore {

    // The store. Key = account number, Value = Account object.
    private final Map<Integer, Account> accounts = new HashMap<>();

    // Auto-incrementing account number. AtomicInteger is thread-safe
    // so two simultaneous openAccount() calls never get the same number.
    private final AtomicInteger nextId = new AtomicInteger(1001);

    // Contains list of subscribers to monitor operations
    public final List<BiConsumer<String, OpResponse<Account>>> updateCallbacks = new ArrayList<>();

    // synchronized means only one thread can be inside this method at a time.
    public synchronized OpResponse<Account> openAccount(String name, String password,
                                                        Currency currency, float initialBalance) {
        int no = nextId.getAndIncrement();
        Account newAcc = new Account(no, name, password, currency, initialBalance);
        accounts.put(no, newAcc);
        updateCallbacks.forEach(cb -> cb.accept("New Account Created", OpResponse.ok(newAcc)));
        return OpResponse.ok(newAcc);
    }

    /**
     * @return Account number
     */
    public synchronized OpResponse<Integer> closeAccount(int no, String name, String password) {
        return get(no, name, password).map(acc -> {
            Account removedAcc = accounts.remove(no);
            String callbackMessage = "Account " + no + " closed";
            updateCallbacks.forEach(cb -> cb.accept(callbackMessage, OpResponse.ok(removedAcc)));
            return no;
        });
    }

    /**
     * @param no       Account number
     * @param currency Currency must match the account's currency, otherwise it's an error.
     * @param amount   Amount to deposit (positive) or withdraw (negative)
     * @return Account Balance
     */
    public synchronized OpResponse<Account> depositWithdraw(int no, String name, String password,
                                                            Currency currency, float amount) {
        return get(no, name, password).then(acc -> {
            if (acc.currency != currency) return OpResponse.error(StatusCode.ERR_CURRENCY_MISMATCH);
            if (amount < 0 && acc.balance + amount < 0) return OpResponse.error(StatusCode.ERR_INSUFFICIENT_FUNDS);

            acc.balance += amount;
            updateCallbacks.forEach(cb -> cb.accept("Account Balance Changed", OpResponse.ok(acc)));
            return OpResponse.ok(acc);
        });
    }

    // transfer — debits src, credits dst atomically (both in same synchronized block).

    /**
     * @return Source account balance after transfer
     */
    public synchronized OpResponse<Account> transfer(int srcNo, int dstNo, String name,
                                                     String password, float amount) {
        return get(srcNo, name, password).then(src -> {
                    Account dst = accounts.get(dstNo);
                    if (dst == null) return OpResponse.error(StatusCode.ERR_DST_ACC_NOT_FOUND);
                    if (src.currency != dst.currency) return OpResponse.error(StatusCode.ERR_CURRENCY_MISMATCH);
                    if (src.balance < amount) return OpResponse.error(StatusCode.ERR_INSUFFICIENT_FUNDS);

                    src.balance -= amount;
                    dst.balance += amount;
                    updateCallbacks.forEach(cb -> cb.accept("Transferred " + amount + " to " + dstNo + " Success!", OpResponse.ok(src)));
                    return OpResponse.ok(src);
                }
        );
    }

    public synchronized OpResponse<Account> get(int accountNo, String name, String password) {
        Account acc = accounts.get(accountNo);
        if (acc == null) return OpResponse.error(StatusCode.ERR_ACC_NOT_FOUND);
        if (!acc.holderName.equals(name)) return OpResponse.error(StatusCode.ERR_NAME_MISMATCH);
        if (!acc.password.equals(password)) return OpResponse.error(StatusCode.ERR_WRONG_PASSWORD);
        return OpResponse.ok(acc);
    }

}