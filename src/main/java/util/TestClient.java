package util;

import model.*;
import util.Marshaller;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Scanner;

/**
 * Standalone Java test client for verifying the server without the C++ client.
 * Sends real UDP packets using the same wire format the C++ client will use.
 *
 * Usage:
 *   java -cp out TestClient [serverHost] [serverPort]
 *
 * Examples:
 *   java -cp out TestClient localhost 2222
 *   java -cp out TestClient 192.168.1.5 2222
 */
public class TestClient {

    private static final int    TIMEOUT_MS  = 3000;
    private static final int    BUFFER_SIZE = 65535;

    private final DatagramSocket socket;
    private final InetAddress    serverAddr;
    private final int            serverPort;
    private       int            requestId  = 1;

    public TestClient(String host, int port) throws Exception {
        this.serverAddr = InetAddress.getByName(host);
        this.serverPort = port;
        this.socket     = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT_MS);
        System.out.println("TestClient connected to " + host + ":" + port);
    }

    // ------------------------------------------------------------------ send/receive

    private Message send(byte[] packet) throws Exception {
        DatagramPacket dp = new DatagramPacket(packet, packet.length, serverAddr, serverPort);
        socket.send(dp);

        byte[] buf = new byte[BUFFER_SIZE];
        DatagramPacket rp = new DatagramPacket(buf, buf.length);
        socket.receive(rp);

        byte[] data = new byte[rp.getLength()];
        System.arraycopy(rp.getData(), 0, data, 0, rp.getLength());
        return Message.fromBytes(data);
    }

    // ------------------------------------------------------------------ operations

    /** Open account. Returns assigned account number, or -1 on error. */
    public int openAccount(String name, String password, Currency currency, float balance) {
        try {
            // Body: currency(1B) | balance(float32) | name(str) | password(str)
            ByteBuffer body = ByteBuffer.allocate(1 + 4 + 2 + name.length() + 2 + password.length() + 10);
            Marshaller.writeByte(body, currency.code);
            Marshaller.writeFloat(body, balance);
            Marshaller.writeString(body, name);
            Marshaller.writeString(body, password);
            body.flip();
            byte[] bodyBytes = new byte[body.limit()];
            body.get(bodyBytes);

            byte[] pkt = new Message(requestId++, OpCode.OPEN_ACCOUNT,
                    Message.TYPE_REQUEST, Message.STATUS_OK, bodyBytes).toBytes();

            Message reply = send(pkt);
            if (reply.status == Message.STATUS_OK) {
                int accountNo = ByteBuffer.wrap(reply.body).getInt();
                System.out.printf("[OK] Opened account %d for %s (%.2f %s)%n",
                        accountNo, name, balance, currency);
                return accountNo;
            } else {
                System.err.println("[ERROR] openAccount: " + readErrorString(reply.body));
                return -1;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[TIMEOUT] openAccount timed out");
            return -1;
        } catch (Exception e) {
            System.err.println("[ERROR] openAccount: " + e.getMessage());
            return -1;
        }
    }

    /** Close account. Returns true on success. */
    public boolean closeAccount(int accountNo, String name, String password) {
        try {
            ByteBuffer body = ByteBuffer.allocate(4 + 2 + name.length() + 2 + password.length() + 10);
            Marshaller.writeInt(body, accountNo);
            Marshaller.writeString(body, name);
            Marshaller.writeString(body, password);
            body.flip();
            byte[] bodyBytes = new byte[body.limit()];
            body.get(bodyBytes);

            byte[] pkt = new Message(requestId++, OpCode.CLOSE_ACCOUNT,
                    Message.TYPE_REQUEST, Message.STATUS_OK, bodyBytes).toBytes();

            Message reply = send(pkt);
            if (reply.status == Message.STATUS_OK) {
                System.out.println("[OK] " + readErrorString(reply.body));
                return true;
            } else {
                System.err.println("[ERROR] closeAccount: " + readErrorString(reply.body));
                return false;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[TIMEOUT] closeAccount timed out");
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] closeAccount: " + e.getMessage());
            return false;
        }
    }

    /** Deposit (positive) or withdraw (negative). Returns new balance, or Float.NaN on error. */
    public float depositWithdraw(int accountNo, String name, String password,
                                 Currency currency, float amount) {
        try {
            ByteBuffer body = ByteBuffer.allocate(4 + 1 + 4 + 2 + name.length() + 2 + password.length() + 10);
            Marshaller.writeInt(body, accountNo);
            Marshaller.writeByte(body, currency.code);
            Marshaller.writeFloat(body, amount);
            Marshaller.writeString(body, name);
            Marshaller.writeString(body, password);
            body.flip();
            byte[] bodyBytes = new byte[body.limit()];
            body.get(bodyBytes);

            byte[] pkt = new Message(requestId++, OpCode.DEPOSIT_WITHDRAW,
                    Message.TYPE_REQUEST, Message.STATUS_OK, bodyBytes).toBytes();

            Message reply = send(pkt);
            if (reply.status == Message.STATUS_OK) {
                float newBal = ByteBuffer.wrap(reply.body).getFloat();
                System.out.printf("[OK] New balance for account %d: %.2f%n", accountNo, newBal);
                return newBal;
            } else {
                System.err.println("[ERROR] depositWithdraw: " + readErrorString(reply.body));
                return Float.NaN;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[TIMEOUT] depositWithdraw timed out");
            return Float.NaN;
        } catch (Exception e) {
            System.err.println("[ERROR] depositWithdraw: " + e.getMessage());
            return Float.NaN;
        }
    }

    /** Query balance (idempotent). Returns balance or Float.NaN on error. */
    public float queryBalance(int accountNo, String name, String password) {
        try {
            ByteBuffer body = ByteBuffer.allocate(4 + 2 + name.length() + 2 + password.length() + 10);
            Marshaller.writeInt(body, accountNo);
            Marshaller.writeString(body, name);
            Marshaller.writeString(body, password);
            body.flip();
            byte[] bodyBytes = new byte[body.limit()];
            body.get(bodyBytes);

            byte[] pkt = new Message(requestId++, OpCode.QUERY_BALANCE,
                    Message.TYPE_REQUEST, Message.STATUS_OK, bodyBytes).toBytes();

            Message reply = send(pkt);
            if (reply.status == Message.STATUS_OK) {
                float bal = ByteBuffer.wrap(reply.body).getFloat();
                System.out.printf("[OK] Balance for account %d: %.2f%n", accountNo, bal);
                return bal;
            } else {
                System.err.println("[ERROR] queryBalance: " + readErrorString(reply.body));
                return Float.NaN;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[TIMEOUT] queryBalance timed out");
            return Float.NaN;
        } catch (Exception e) {
            System.err.println("[ERROR] queryBalance: " + e.getMessage());
            return Float.NaN;
        }
    }

    /** Transfer funds. Returns new source balance or Float.NaN on error. */
    public float transfer(int srcNo, int dstNo, String name, String password, float amount) {
        try {
            ByteBuffer body = ByteBuffer.allocate(4 + 4 + 4 + 2 + name.length() + 2 + password.length() + 10);
            Marshaller.writeInt(body, srcNo);
            Marshaller.writeInt(body, dstNo);
            Marshaller.writeFloat(body, amount);
            Marshaller.writeString(body, name);
            Marshaller.writeString(body, password);
            body.flip();
            byte[] bodyBytes = new byte[body.limit()];
            body.get(bodyBytes);

            byte[] pkt = new Message(requestId++, OpCode.TRANSFER_FUNDS,
                    Message.TYPE_REQUEST, Message.STATUS_OK, bodyBytes).toBytes();

            Message reply = send(pkt);
            if (reply.status == Message.STATUS_OK) {
                float newBal = ByteBuffer.wrap(reply.body).getFloat();
                System.out.printf("[OK] Transfer done. New balance for account %d: %.2f%n", srcNo, newBal);
                return newBal;
            } else {
                System.err.println("[ERROR] transfer: " + readErrorString(reply.body));
                return Float.NaN;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[TIMEOUT] transfer timed out");
            return Float.NaN;
        } catch (Exception e) {
            System.err.println("[ERROR] transfer: " + e.getMessage());
            return Float.NaN;
        }
    }

    /** Register for monitoring. Blocks for the interval, printing any callbacks received. */
    public void registerMonitor(int intervalSeconds) {
        try {
            ByteBuffer body = ByteBuffer.allocate(4);
            Marshaller.writeInt(body, intervalSeconds);

            byte[] pkt = new Message(requestId++, OpCode.REGISTER_MONITOR,
                    Message.TYPE_REQUEST, Message.STATUS_OK, body.array()).toBytes();

            Message reply = send(pkt);
            if (reply.status != Message.STATUS_OK) {
                System.err.println("[ERROR] registerMonitor: " + readErrorString(reply.body));
                return;
            }
            System.out.println("[OK] " + readErrorString(reply.body));
            System.out.println("[Monitor] Waiting for callbacks for " + intervalSeconds + "s ...");

            // Listen for callbacks until the interval expires
            long deadline = System.currentTimeMillis() + intervalSeconds * 1000L;
            socket.setSoTimeout(500);
            while (System.currentTimeMillis() < deadline) {
                try {
                    byte[] buf = new byte[BUFFER_SIZE];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    socket.receive(dp);
                    byte[] data = new byte[dp.getLength()];
                    System.arraycopy(dp.getData(), 0, data, 0, dp.getLength());
                    printCallback(data);
                } catch (SocketTimeoutException ignored) { /* keep polling */ }
            }
            socket.setSoTimeout(TIMEOUT_MS);
            System.out.println("[Monitor] Interval expired.");
        } catch (Exception e) {
            System.err.println("[ERROR] registerMonitor: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private String readErrorString(byte[] body) {
        try {
            return Marshaller.readString(ByteBuffer.wrap(body));
        } catch (Exception e) {
            return "(could not decode message)";
        }
    }

    private void printCallback(byte[] data) {
        try {
            // callback body: accountNo(int32) | currency(1B) | newBalance(float32)
            // skip 12-byte header
            ByteBuffer buf = ByteBuffer.wrap(data, 12, data.length - 12);
            int   accountNo  = buf.getInt();
            byte  currByte   = buf.get();
            float newBalance = Float.intBitsToFloat(buf.getInt());
            Currency currency = Currency.from(currByte);
            System.out.printf("[Callback] Account %d updated: %.2f %s%n",
                    accountNo, newBalance, currency);
        } catch (Exception e) {
            System.err.println("[Callback] Could not parse callback: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ automated tests

    public static void runTests(TestClient client) {
        System.out.println("\n========== AUTOMATED TESTS ==========");

        // Test 1: open accounts
        System.out.println("\n--- Test 1: Open accounts ---");
        int aliceNo = client.openAccount("Alice", "pass1234", Currency.SGD, 1000.0f);
        int bobNo   = client.openAccount("Bob",   "pass5678", Currency.SGD, 500.0f);
        assert aliceNo > 0 : "Alice account creation failed";
        assert bobNo   > 0 : "Bob account creation failed";

        // Test 2: query balance (idempotent)
        System.out.println("\n--- Test 2: Query balance (idempotent) ---");
        float bal1 = client.queryBalance(aliceNo, "Alice", "pass1234");
        float bal2 = client.queryBalance(aliceNo, "Alice", "pass1234");
        assert bal1 == bal2 : "Idempotent query returned different results";

        // Test 3: deposit
        System.out.println("\n--- Test 3: Deposit ---");
        float afterDeposit = client.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, 200.0f);
        assert afterDeposit == 1200.0f : "Expected 1200 after deposit, got " + afterDeposit;

        // Test 4: withdraw
        System.out.println("\n--- Test 4: Withdraw ---");
        float afterWithdraw = client.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, -100.0f);
        assert afterWithdraw == 1100.0f : "Expected 1100 after withdraw, got " + afterWithdraw;

        // Test 5: withdraw insufficient funds
        System.out.println("\n--- Test 5: Overdraft (should fail) ---");
        float shouldFail = client.depositWithdraw(aliceNo, "Alice", "pass1234", Currency.SGD, -9999.0f);
        assert Float.isNaN(shouldFail) : "Overdraft should have failed";

        // Test 6: wrong password
        System.out.println("\n--- Test 6: Wrong password (should fail) ---");
        float wrongPwd = client.queryBalance(aliceNo, "Alice", "wrongpwd");
        assert Float.isNaN(wrongPwd) : "Wrong password should have failed";

        // Test 7: transfer
        System.out.println("\n--- Test 7: Transfer ---");
        float afterTransfer = client.transfer(aliceNo, bobNo, "Alice", "pass1234", 300.0f);
        assert afterTransfer == 800.0f : "Expected 800 after transfer, got " + afterTransfer;
        float bobBal = client.queryBalance(bobNo, "Bob", "pass5678");
        assert bobBal == 800.0f : "Expected Bob to have 800, got " + bobBal;

        // Test 8: close account
        System.out.println("\n--- Test 8: Close account ---");
        boolean closed = client.closeAccount(bobNo, "Bob", "pass5678");
        assert closed : "Closing Bob's account failed";

        // Test 9: query closed account (should fail)
        System.out.println("\n--- Test 9: Query closed account (should fail) ---");
        float closedQuery = client.queryBalance(bobNo, "Bob", "pass5678");
        assert Float.isNaN(closedQuery) : "Querying closed account should fail";

        System.out.println("\n========== ALL TESTS PASSED ==========\n");
    }

    // ------------------------------------------------------------------ interactive menu

    public static void runInteractive(TestClient client) {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- Menu ---");
            System.out.println("1. Open account");
            System.out.println("2. Close account");
            System.out.println("3. Deposit / Withdraw");
            System.out.println("4. Query balance");
            System.out.println("5. Transfer funds");
            System.out.println("6. Register monitor");
            System.out.println("0. Exit");
            System.out.print("Choice: ");
            int choice = Integer.parseInt(sc.nextLine().trim());

            if (choice == 0) break;
            else if (choice == 1) {
                System.out.print("Name: ");      String name = sc.nextLine();
                System.out.print("Password: ");  String pwd  = sc.nextLine();
                System.out.print("Currency (SGD/USD/EUR/GBP): "); Currency cur = Currency.valueOf(sc.nextLine().toUpperCase());
                System.out.print("Initial balance: "); float bal = Float.parseFloat(sc.nextLine());
                client.openAccount(name, pwd, cur, bal);
            } else if (choice == 2) {
                System.out.print("Account no: "); int no = Integer.parseInt(sc.nextLine());
                System.out.print("Name: ");       String name = sc.nextLine();
                System.out.print("Password: ");   String pwd  = sc.nextLine();
                client.closeAccount(no, name, pwd);
            } else if (choice == 3) {
                System.out.print("Account no: "); int no = Integer.parseInt(sc.nextLine());
                System.out.print("Name: ");       String name = sc.nextLine();
                System.out.print("Password: ");   String pwd  = sc.nextLine();
                System.out.print("Currency: ");   Currency cur = Currency.valueOf(sc.nextLine().toUpperCase());
                System.out.print("Amount (+deposit / -withdraw): "); float amt = Float.parseFloat(sc.nextLine());
                client.depositWithdraw(no, name, pwd, cur, amt);
            } else if (choice == 4) {
                System.out.print("Account no: "); int no = Integer.parseInt(sc.nextLine());
                System.out.print("Name: ");       String name = sc.nextLine();
                System.out.print("Password: ");   String pwd  = sc.nextLine();
                client.queryBalance(no, name, pwd);
            } else if (choice == 5) {
                System.out.print("Source account no: "); int src = Integer.parseInt(sc.nextLine());
                System.out.print("Dest account no: ");   int dst = Integer.parseInt(sc.nextLine());
                System.out.print("Amount: ");             float amt = Float.parseFloat(sc.nextLine());
                System.out.print("Your name: ");          String name = sc.nextLine();
                System.out.print("Your password: ");      String pwd  = sc.nextLine();
                client.transfer(src, dst, name, pwd, amt);
            } else if (choice == 6) {
                System.out.print("Monitor interval (seconds): "); int secs = Integer.parseInt(sc.nextLine());
                client.registerMonitor(secs);
            }
        }
        System.out.println("Bye.");
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : 2222;
        String mode = args.length > 2 ? args[2] : "interactive"; // "test" or "interactive"

        TestClient client = new TestClient(host, port);

        if (mode.equals("test")) {
            runTests(client);
        } else {
            runInteractive(client);
        }
    }
}