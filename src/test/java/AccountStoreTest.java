import model.AccountStore;
import model.Currency;

/**
 * Standalone test for AccountStore. No networking, no UDP, no handlers.
 * Run this directly from IntelliJ by right-clicking → Run 'AccountStoreTest.main()'.
 *
 * Each test prints PASS or FAIL with a short explanation.
 * A failing assertion prints what it got vs what it expected, then keeps running
 * so you see all failures at once.
 */
public class AccountStoreTest {

    static int passed = 0;
    static int failed = 0;

    // ------------------------------------------------------------------ helpers

    static void expect(String testName, boolean condition, String detail) {
        if (condition) {
            System.out.println("  PASS  " + testName);
            passed++;
        } else {
            System.out.println("  FAIL  " + testName + " — " + detail);
            failed++;
        }
    }

    static void section(String name) {
        System.out.println("\n=== " + name + " ===");
    }

    // ------------------------------------------------------------------ tests

    static void testOpenAccount(AccountStore store) {
        section("openAccount");

        int aliceNo = store.openAccount("Alice", "pass1234", Currency.SGD, 1000f);
        int bobNo   = store.openAccount("Bob",   "pass5678", Currency.USD, 500f);
        int carolNo = store.openAccount("Carol", "pass9999", Currency.SGD, 250f);

        expect("Alice gets account number > 0",
                aliceNo > 0, "got " + aliceNo);

        expect("Bob gets a different number than Alice",
                bobNo != aliceNo, "both got " + aliceNo);

        expect("Account numbers are sequential",
                bobNo == aliceNo + 1, "alice=" + aliceNo + " bob=" + bobNo);

        expect("Carol gets next number",
                carolNo == bobNo + 1, "bob=" + bobNo + " carol=" + carolNo);

        // Verify account was actually stored by querying it back
        Float bal = store.queryBalance(aliceNo, "Alice", "pass1234");
        expect("Alice's balance is 1000 after opening",
                bal != null && bal == 1000f, "got " + bal);
    }

    static void testQueryBalance(AccountStore store, int aliceNo) {
        section("queryBalance");

        Float bal = store.queryBalance(aliceNo, "Alice", "pass1234");
        expect("Correct credentials return a balance",
                bal != null, "returned null");

        expect("Balance matches what was deposited at open",
                bal != null && bal == 1000f, "got " + bal);

        Float wrongPwd = store.queryBalance(aliceNo, "Alice", "wrongpass");
        expect("Wrong password returns null",
                wrongPwd == null, "expected null, got " + wrongPwd);

        Float wrongName = store.queryBalance(aliceNo, "NotAlice", "pass1234");
        expect("Wrong name returns null",
                wrongName == null, "expected null, got " + wrongName);

        Float noAccount = store.queryBalance(9999, "Alice", "pass1234");
        expect("Non-existent account number returns null",
                noAccount == null, "expected null, got " + noAccount);
    }

    static void testDeposit(AccountStore store, int aliceNo) {
        section("depositWithdraw — deposits");

        float bal = store.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, 200f);
        expect("Deposit 200 → balance becomes 1200",
                bal == 1200f, "got " + bal);

        float bal2 = store.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, 50f);
        expect("Deposit 50 more → balance becomes 1250",
                bal2 == 1250f, "got " + bal2);
    }

    static void testWithdraw(AccountStore store, int aliceNo) {
        section("depositWithdraw — withdrawals");

        float bal = store.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, -100f);
        expect("Withdraw 100 → balance decreases by 100",
                bal == 1150f, "got " + bal);
    }

    static void testWithdrawErrors(AccountStore store, int aliceNo) {
        section("depositWithdraw — error cases");

        // Wrong password
        try {
            store.depositWithdraw(aliceNo, "Alice", "wrongpass", Currency.SGD, 10f);
            expect("Wrong password should throw", false, "no exception thrown");
        } catch (IllegalArgumentException e) {
            expect("Wrong password throws IllegalArgumentException",
                    true, "");
        }

        // Wrong name
        try {
            store.depositWithdraw(aliceNo, "NotAlice", "pass1234", Currency.SGD, 10f);
            expect("Wrong name should throw", false, "no exception thrown");
        } catch (IllegalArgumentException e) {
            expect("Wrong name throws IllegalArgumentException", true, "");
        }

        // Currency mismatch (Alice's account is SGD, trying USD)
        try {
            store.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.USD, 10f);
            expect("Currency mismatch should throw", false, "no exception thrown");
        } catch (IllegalArgumentException e) {
            expect("Currency mismatch throws IllegalArgumentException", true, "");
        }

        // Overdraft
        try {
            store.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, -999999f);
            expect("Overdraft should throw", false, "no exception thrown");
        } catch (IllegalArgumentException e) {
            expect("Overdraft throws IllegalArgumentException", true, "");
        }

        // Confirm balance is unchanged after all the failed attempts
        Float bal = store.queryBalance(aliceNo, "Alice", "pass1234");
        expect("Balance unchanged after all failed operations",
                bal != null && bal == 1150f, "got " + bal);
    }

    static void testTransfer(AccountStore store, int aliceNo, int bobNo) {
        section("transfer");

        // Alice (SGD 1150) transfers 300 to Carol — but wait, Bob is USD.
        // We need two SGD accounts. Alice = SGD 1150, Carol is the other SGD account.
        // Let's use a fresh store for this — actually let's just open a new SGD account.
        int daveNo = store.openAccount("Dave", "davepass", Currency.SGD, 0f);

        String err = store.transfer(aliceNo, daveNo, "Alice", "pass1234", 300f);
        expect("Valid transfer returns null (success)",
                err == null, "got error: " + err);

        Float aliceBal = store.queryBalance(aliceNo, "Alice", "pass1234");
        Float daveBal  = store.queryBalance(daveNo,  "Dave",  "davepass");
        expect("Alice's balance decreased by 300",
                aliceBal != null && aliceBal == 850f, "got " + aliceBal);
        expect("Dave's balance increased by 300",
                daveBal != null && daveBal == 300f, "got " + daveBal);

        // Transfer to non-existent account
        String noAcct = store.transfer(aliceNo, 9999, "Alice", "pass1234", 10f);
        expect("Transfer to missing account returns error string",
                noAcct != null, "expected error, got null");

        // Insufficient balance
        String overdrawn = store.transfer(aliceNo, daveNo, "Alice", "pass1234", 99999f);
        expect("Overdraft on transfer returns error string",
                overdrawn != null, "expected error, got null");

        // Wrong password
        String wrongPwd = store.transfer(aliceNo, daveNo, "Alice", "wrongpass", 10f);
        expect("Wrong password on transfer returns error string",
                wrongPwd != null, "expected error, got null");

        // Balances unchanged after failed transfers
        Float aliceAfter = store.queryBalance(aliceNo, "Alice", "pass1234");
        expect("Alice's balance unchanged after failed transfers",
                aliceAfter != null && aliceAfter == 850f, "got " + aliceAfter);
    }

    static void testCloseAccount(AccountStore store, int bobNo) {
        section("closeAccount");

        // Wrong password
        String wrongPwd = store.closeAccount(bobNo, "Bob", "wrongpass");
        expect("Wrong password returns error string",
                wrongPwd != null, "expected error, got null");

        // Wrong name
        String wrongName = store.closeAccount(bobNo, "NotBob", "pass5678");
        expect("Wrong name returns error string",
                wrongName != null, "expected error, got null");

        // Verify account still exists after failed closes
        Float stillThere = store.queryBalance(bobNo, "Bob", "pass5678");
        expect("Account still exists after failed close attempts",
                stillThere != null, "account was deleted");

        // Correct credentials — should succeed
        String success = store.closeAccount(bobNo, "Bob", "pass5678");
        expect("Correct credentials returns null (success)",
                success == null, "got error: " + success);

        // Now account should be gone
        Float gone = store.queryBalance(bobNo, "Bob", "pass5678");
        expect("Querying closed account returns null",
                gone == null, "expected null, got " + gone);

        // Closing again should return an error (account no longer exists)
        String doubleClose = store.closeAccount(bobNo, "Bob", "pass5678");
        expect("Closing already-closed account returns error",
                doubleClose != null, "expected error, got null");
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        System.out.println("AccountStore unit tests");
        System.out.println("========================");

        AccountStore store = new AccountStore();

        // Each test section passes the account numbers it needs from earlier sections.
        // We open Alice and Bob once at the top and reuse them throughout.
        int aliceNo = store.openAccount("Alice", "pass1234", Currency.SGD, 1000f);
        int bobNo   = store.openAccount("Bob",   "pass5678", Currency.USD, 500f);

        testOpenAccount(new AccountStore());   // fresh store for open tests
        testQueryBalance(store, aliceNo);
        testDeposit(store, aliceNo);
        testWithdraw(store, aliceNo);
        testWithdrawErrors(store, aliceNo);
        testTransfer(store, aliceNo, bobNo);
        testCloseAccount(store, bobNo);

        // Summary
        System.out.println("\n========================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
        if (failed == 0)
            System.out.println("All tests passed.");
        else
            System.out.println("Some tests FAILED — check output above.");
    }
}