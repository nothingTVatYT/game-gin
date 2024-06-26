package net.nothingtv.game.storage.common;

import java.util.HexFormat;

public class Storage {
    public static final byte GET = 1;
    public static final byte INSERT = 2;
    public static final byte UPDATE = 3;
    public static final byte DELETE = 4;
    public static final byte UID = 5;

    public static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    public static String toHex(byte[] bytes, int offset, int length) {
        return HexFormat.of().formatHex(bytes, offset, offset + length);
    }
}
