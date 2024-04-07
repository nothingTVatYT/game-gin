package net.nothingtv.game.network.server;

import net.nothingtv.game.network.data.Player;
import net.nothingtv.game.network.message.Messages;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.logging.*;

public class GameServer {

    public static short Version = 1;
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private GameServerConfig config;
    private boolean active;

    public GameServer() {
        try {
            Handler handler = new FileHandler("gameserver.log");
            handler.setFormatter(new SimpleFormatter());
            LOG.addHandler(handler);
        } catch (Exception e) {}
        loadConfig();
    }

    public void start() {
        active = true;
        new Thread(this::startServerComm).start();

        LOG.info("Game server starting on " + config.gameServer + " " + config.gamePort);
        try (ServerSocketChannel socket = ServerSocketChannel.open()) {
            socket.bind(new InetSocketAddress(config.gameServer, config.gamePort));
            while (active) {
                new Thread(new GameSession(this, socket.accept())).start();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "GS cannot create server socket (client) on " + config.gamePort, ex);
        }
    }

    private void startServerComm() {
        LOG.log(Level.INFO, "GS comm server starting on " + config.commServer + ":" + config.commPort);
        try (ServerSocketChannel socket = ServerSocketChannel.open()) {
            socket.bind(new InetSocketAddress(config.commServer, config.commPort));
            while (active) {
                new Thread(new CommSession(this, socket.accept())).start();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "GS cannot create server socket (comm) on " + config.gamePort, ex);
        }
    }

    protected void store(String login, int token) {

    }

    protected int checkLSToken(int token) {
        return 1;
    }

    private void loadConfig() {
        config = new GameServerConfig();
    }

    public boolean authenticate(String user, int token) {
        return true;
    }

    public Player getPlayer(String login, String name) {
        Player player = new Player();
        player.name = name;
        player.pos.set(200, 10, 200);
        player.direction.set(0, 0, 1);
        return player;
    }

    public static void main(String[] args) {
        Messages.init();
        new GameServer().start();
    }
}
