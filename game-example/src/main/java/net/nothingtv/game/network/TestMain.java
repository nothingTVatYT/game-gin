package net.nothingtv.game.network;

import net.nothingtv.game.network.client.GameClient;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.server.GameServer;
import net.nothingtv.game.network.server.LoginServer;

public class TestMain {

    public static void main(String[] args) {

        //Logger.getGlobal().setLevel(Level.FINE);
        //LoggingSetup.setup();

        Messages.init();

        System.out.println("Start game server");
        new Thread(() -> new GameServer().start()).start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Start login server");
        new Thread(() -> new LoginServer().start()).start();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Start test game client");
        new Thread(new GameClient(GameClient.GameClientConfig.localTestConfig)).start();
    }
}
