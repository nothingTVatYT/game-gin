package net.nothingtv.game.network;

public class Tools {

    public static void nap(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }

    public static byte[] toBytes(int i) {
        byte[] bytes = new byte[4];
        bytes[3] = (byte)(i & 0xff);
        bytes[2] = (byte)((i >> 8) & 0xff);
        bytes[1] = (byte)((i >> 16) & 0xff);
        bytes[0] = (byte)((i >> 24) & 0xff);
        return bytes;
    }

    public static int fromBytes(byte[] bytes) {
        return (int)bytes[0] << 24 + (int)bytes[1] << 16 + (int)bytes[2] << 8 + (int)bytes[3];
    }

}
