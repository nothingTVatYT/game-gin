package net.nothingtv.game.network.server;

import net.nothingtv.game.network.Tools;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.storage.client.Client;
import net.nothingtv.game.storage.common.StorageConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.*;

public class LoginServer {

    public static short Version = 1;
    private static final Logger LOG = Logger.getLogger(LoginServer.class.getName());

    private LoginServerConfig config;
    private boolean active;
    private final ConcurrentHashMap<String, Long> expectedUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, GameServerConnection> commConnections = new ConcurrentHashMap<>();
    private final Random rnd = new Random();
    private ServerSocketChannel serverSocketChannel;
    private final DBClient dbClient;

    public LoginServer() {
        try {
            Path dir = Paths.get("log");
            if (!Files.isDirectory(dir))
                Files.createDirectory(dir);
            Handler handler = new FileHandler("log/loginserver.log");
            handler.setFormatter(new SimpleFormatter());
            Logger.getGlobal().setLevel(Level.FINE);
            LOG.addHandler(handler);
        } catch (Exception e) {}
        loadConfig();
        dbClient = new DBClient();
    }

    public void start() {

        new Thread(() -> {
            Tools.nap(200);
            startGameServerCommunication();
        }).start();

        active = true;
        try (ServerSocketChannel socket = ServerSocketChannel.open()) {
            serverSocketChannel = socket;
            socket.bind(new InetSocketAddress(config.loginServer, config.loginPort));
            while (active) {
                new Thread(new LoginSession(this, socket.accept())).start();
            }
        } catch (ClosedChannelException e) {
            LOG.log(Level.INFO, "LS socket closed - exiting");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot create server socket on " + config.loginPort, ex);
        }
        dbClient.close();
        LOG.info("Login server stopped.");
    }

    public void shutdown() {
        active = false;
        if (serverSocketChannel != null) {
            try {
                serverSocketChannel.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Cannot close server socket channel", e);
            }
        }
    }

    private void startGameServerCommunication() {
        for (int i = 0; i < config.gameServersInternal.length; i++) {
            String host = config.gameServersInternal[i];
            int commPort = config.gameServerCommPorts[i];
            LOG.info("Start LS->GS connection to " + host + " " + commPort);
            GameServerConnection commConn = new GameServerConnection(this, host, commPort);
            commConnections.put(i, commConn);
            new Thread(commConn).start();
        }
    }

    private void loadConfig() {
        config = new LoginServerConfig();
    }

    protected void sendToGS(int index, Message message) {
        GameServerConnection conn = commConnections.get(index);
        if (conn == null) {
            LOG.log(Level.WARNING, "LS cannot send message " + message.getClass().getName() + ": There is no connection for " + index);
        } else
            conn.send(message);
    }

    public boolean authenticate(String user, String password) {
        return dbClient.checkUserPassword(user, password);
    }

    public String getGameServers() {
        return config.gameServersCoded;
    }

    public int getCommToken() {
        try {
            String content = Files.readString(Paths.get("comm-token"));
            return Integer.parseInt(content.strip());
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "There is no comm token defined", e);
        }
        return rnd.nextInt();
    }

    public boolean checkGSToken(int token) {
        try {
            String content = Files.readString(Paths.get("gs-token"));
            return Integer.parseInt(content.strip()) == token;
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "There is no game server token defined", e);
        }
        return false;
    }

    public void addExpectedUser(String user) {
        expectedUsers.put(user, System.currentTimeMillis());
    }

    public boolean isExpectedUser(String user) {
        return expectedUsers.containsKey(user);
    }

    public int createUserToken(String name) {
        return rnd.nextInt();
    }

    public static void main(String[] args) {
        Messages.init();
        new LoginServer().start();
    }
}
