package model;

public enum StatusCode {
    // general system status
    STATUS_OK(0),
    ERR_INTERNAL(1),
    ERR_MALFORMED_REQ(2),

    // general account errors
    ERR_INSUFFICIENT_FUNDS(12),
    ERR_CURRENCY_MISMATCH(13),

    // account auth errors (src)
    ERR_ACC_NOT_FOUND(21),
    ERR_WRONG_PASSWORD(22),
    ERR_NAME_MISMATCH(23),
    // account auth errors (dst)
    ERR_DST_ACC_NOT_FOUND(31),
    ERR_DST_WRONG_PASSWORD(32),
    ERR_DST_NAME_MISMATCH(33);

    public final byte code;

    StatusCode(int code) {
        this.code = (byte) code;
    }

    public static StatusCode from(byte b) {
        for (StatusCode s : values())
            if (s.code == b) return s;
        throw new IllegalArgumentException("Unknown status code: 0x" + Integer.toHexString(b & 0xFF));
    }
}
