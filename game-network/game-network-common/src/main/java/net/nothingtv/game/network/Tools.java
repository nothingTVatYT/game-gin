package net.nothingtv.game.network;

public class Tools {

    public static void nap(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }
}
