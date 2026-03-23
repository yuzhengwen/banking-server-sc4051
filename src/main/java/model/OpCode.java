// model/OpCode.java
package model;

public enum OpCode {
    OPEN_ACCOUNT    (0x01),
    CLOSE_ACCOUNT   (0x02),
    DEPOSIT_WITHDRAW(0x03),
    REGISTER_MONITOR(0x04),
    QUERY_BALANCE   (0x05),
    TRANSFER_FUNDS  (0x06);

    public final byte code;

    // Constructor sets the byte value for each constant
    OpCode(int code) { this.code = (byte) code; }

    // from() does the reverse lookup: given a raw byte, return the enum constant.
    // Throws if the byte doesn't match any known opcode — this is how you catch
    // malformed or unsupported packets early.
    public static OpCode from(byte b) {
        for (OpCode op : values())
            if (op.code == b) return op;
        throw new IllegalArgumentException("Unknown opcode: 0x" + Integer.toHexString(b & 0xFF));
    }
}