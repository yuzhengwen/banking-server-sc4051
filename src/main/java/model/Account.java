// model/Account.java
package model;

public class Account {
    public final int      accountNumber;
    public final String   holderName;
    public final String   password;
    public       Currency currency;  // mutable — currency could theoretically change
    public       float    balance;   // mutable — changes on every deposit/withdraw

    public Account(int accountNumber, String holderName, String password,
                   Currency currency, float balance) {
        this.accountNumber = accountNumber;
        this.holderName    = holderName;
        this.password      = password;
        this.currency      = currency;
        this.balance       = balance;
    }
}