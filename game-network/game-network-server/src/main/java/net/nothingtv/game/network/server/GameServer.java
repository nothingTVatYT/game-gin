package net.nothingtv.game.network.server;

import net.nothingtv.game.network.data.Player;
import net.nothingtv.game.network.message.Messages;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;

public class GameServer {

    public static short Version = 1;
    private static final Logger LOG = Logger.getLogger(GameServer.class.getName());

    private final ConcurrentHashMap<Integer, String> expectedUser = new ConcurrentHashMap<>();
    private GameServerConfig config;
    private boolean active;
    private ServerSocketChannel serverSocketChannel;
    private final DBClient dbClient;

    public GameServer() {
        try {
            Path dir = Paths.get("log");
            if (!Files.isDirectory(dir))
                Files.createDirectory(dir);
            Handler handler = new FileHandler("log/gameserver.log");
            handler.setFormatter(new SimpleFormatter());
            LOG.addHandler(handler);
        } catch (Exception e) {}
        loadConfig();
        dbClient = new DBClient();
    }

    public void start() {
        active = true;
        new Thread(this::startServerComm).start();

        LOG.info("Game server starting on " + config.gameServer + " " + config.gamePort);
        try (ServerSocketChannel socket = ServerSocketChannel.open()) {
            serverSocketChannel = socket;
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
            serverSocketChannel = socket;
            while (active) {
                new Thread(new CommSession(this, socket.accept())).start();
            }
        } catch (ClosedChannelException e) {
            LOG.log(Level.INFO, "GS channel closed - exiting");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "GS cannot create server socket (comm) on " + config.gamePort, ex);
        }
    }

    protected void store(String login, int token) {
        expectedUser.put(token, login);
    }

    protected int checkLSToken(int token) {
        try {
            String content = Files.readString(Paths.get("comm-token"));
            if (Integer.parseInt(content.strip()) != token)
                return 0;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "GS: There is no LS->GS token defined", e);
        }
        try {
            String content = Files.readString(Paths.get("gs-token"));
            return Integer.parseInt(content.strip());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "GS: There is no GS->LS token defined", e);
        }
        return 0;
    }

    private void loadConfig() {
        config = new GameServerConfig();
    }

    public boolean authenticate(String user, int token) {
        return expectedUser.get(token).equals(user);
    }

    public Player getPlayer(String login, String name) {
        Player player = new Player();
        player.name = name;
        player.login = login;
        try {
            Player loadedPlayer = dbClient.loadPlayer(player);
            if (loadedPlayer != null) return loadedPlayer;
            // for the moment we simply create a new player if there's none
            player.pos.set(200, 10, 200);
            player.direction.set(0, 0, 1);
            dbClient.addPlayer(player);
            return player;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void shutdown() {
        active = false;
        try {
            serverSocketChannel.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot close server socket", e);
        }
    }

    public static void main(String[] args) {
        Messages.init();
        new GameServer().start();
    }
}
