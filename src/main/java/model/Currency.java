// model/Currency.java — identical pattern
package model;

public enum Currency {
    SGD(0), USD(1), EUR(2), GBP(3);

    public final byte code;
    Currency(int code) { this.code = (byte) code; }

    public static Currency from(byte b) {
        for (Currency c : values())
            if (c.code == b) return c;
        throw new IllegalArgumentException("Unknown currency: " + b);
    }
}