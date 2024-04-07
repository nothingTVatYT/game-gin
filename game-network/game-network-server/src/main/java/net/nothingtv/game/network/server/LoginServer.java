package net.nothingtv.game.network.server;

import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.Messages;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
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

    public LoginServer() {
        try {
            Handler handler = new FileHandler("loginserver.log");
            handler.setFormatter(new SimpleFormatter());
            Logger.getGlobal().setLevel(Level.FINE);
            LOG.addHandler(handler);
        } catch (Exception e) {}
        loadConfig();
    }

    public void start() {

        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            startGameServerCommunication();
        }).start();

        active = true;
        try (ServerSocketChannel socket = ServerSocketChannel.open()) {
            socket.bind(new InetSocketAddress(config.loginServer, config.loginPort));
            while (active) {
                new Thread(new LoginSession(this, socket.accept())).start();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot create server socket on " + config.loginPort, ex);
        }
        LOG.info("Login server stopped.");
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
        return true;
    }

    public String getGameServers() {
        return config.gameServersCoded;
    }

    public int getCommToken() {
        return 1;
    }

    public boolean checkGSToken(int token) {
        return true;
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
