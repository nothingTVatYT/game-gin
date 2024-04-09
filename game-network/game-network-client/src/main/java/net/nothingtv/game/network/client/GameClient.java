package net.nothingtv.game.network.client;

import net.nothingtv.game.network.MessageHandler;
import net.nothingtv.game.network.NetworkReader;
import net.nothingtv.game.network.NetworkWriter;
import net.nothingtv.game.network.Tools;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.MessageRegister;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.message.impl.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.*;

public class GameClient implements Runnable, MessageHandler {

    public static short Version = 1;

    public static final Logger LOG = Logger.getLogger(GameClient.class.getName());

    public static class GameClientConfig {
        public String loginServer;
        public int loginPort;

        public GameClientConfig(String loginServer, int loginPort) {
            this.loginServer = loginServer;
            this.loginPort = loginPort;
        }

        public static GameClientConfig localTestConfig = new GameClientConfig("localhost", 4781);
    }

    private final GameClientConfig config;
    private NetworkWriter loginServerOutChannel;
    private NetworkWriter gameServerOutChannel;
    private volatile boolean inLoginServer;
    private boolean loggedIn = false;
    private String gameServer;
    private int gameServerPort;
    private String myLogin;
    private int token;

    public GameClient(GameClientConfig config) {
        try {
            Handler handler = new FileHandler("client.log");
            handler.setFormatter(new SimpleFormatter());
            Logger.getGlobal().setLevel(Level.FINE);
            Logger.getGlobal().addHandler(handler);
            Messages.init();
        } catch (Exception e) {}
        this.config = config;
    }

    public void run() {
        inLoginServer = true;
        SocketChannel loginServerSocket;
        LOG.info("Connecting to login server " + config.loginServer + ":" + config.loginPort);
        try {
            loginServerSocket = SocketChannel.open();
            if (!loginServerSocket.connect(new InetSocketAddress(config.loginServer, config.loginPort)))
                loginServerSocket.finishConnect();
            new Thread(new NetworkReader(loginServerSocket, this)).start();
            new Thread(loginServerOutChannel = new NetworkWriter(loginServerSocket)).start();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot create client socket for " + config.loginPort, ex);
            return;
        }
        while (inLoginServer) {
            Tools.nap(100);
        }

        try {
            loginServerSocket.close();
            loginServerOutChannel.stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (token == 0) {
            LOG.info("No token - exiting");
            return;
        }

        LOG.info("Connecting to game server " + gameServer + ":" + gameServerPort);
        try (SocketChannel socket = SocketChannel.open()) {
            if (!socket.connect(new InetSocketAddress(gameServer, gameServerPort)))
                socket.finishConnect();
            new Thread(new NetworkReader(socket, this)).start();
            new Thread(gameServerOutChannel = new NetworkWriter(socket)).start();
            while (socket.isConnected()) {
                Tools.nap(1000);
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot create client socket for " + config.loginPort, ex);
        }
        gameServerOutChannel.stop();
        LOG.info("Game client stopped.");
    }

    protected String getLogin() {
        return "user01";
    }

    protected String getPassword() {
        return "secret";
    }

    @Override
    public void handleMessage(Message message) {
        MessageRegister.MessageId id = MessageRegister.MessageId.values[message.getMessageId()];
        switch(id) {
            case LoginServerGreeting -> {
                LoginServerGreeting greeting = (LoginServerGreeting) message;
                if (greeting.version != GameClient.Version) {
                    versionMismatch(greeting.version);
                    return;
                }
                LoginRequest request = Messages.obtain(LoginRequest.class);
                myLogin = getLogin();
                request.login = myLogin;
                request.password = getPassword();
                loginServerOutChannel.send(request);
            }
            case LoginReply -> {
                LoginReply reply = (LoginReply) message;
                if (!reply.accepted) {
                    loginFailed(reply.message);
                    return;
                }
                ChooseServerRequest csr = Messages.obtain(ChooseServerRequest.class);
                String s = chooseServer(reply.servers);
                String[] parts = s.split(":");
                gameServer = parts[0];
                gameServerPort = Integer.parseInt(parts[1]);
                csr.serverNumber = 0;
                loginServerOutChannel.send(csr);
            }
            case GameServerToken -> {
                GameServerToken reply = (GameServerToken) message;
                token = reply.token;
                inLoginServer = false;
            }
            case GameServerGreeting -> {
                GameServerGreeting greeting = (GameServerGreeting) message;
                LOG.log(Level.INFO, "greeting from gs");
                if (greeting.version != Version) {
                    versionMismatch(greeting.version);
                    return;
                }
                GameServerLoginRequest req = Messages.obtain(GameServerLoginRequest.class);
                req.login = myLogin;
                req.token = token;
                gameServerOutChannel.send(req);
            }
            case GameServerLoginReply -> {
                GameServerLoginReply reply = (GameServerLoginReply) message;
                if (reply.accepted && reply.characters.length > 0) {
                    ChooseCharacter cc = Messages.obtain(ChooseCharacter.class);
                    cc.character = chooseCharacter(reply.characters);
                    gameServerOutChannel.send(cc);
                    loggedIn = true;
                } else {
                    loginFailed("");
                }
            }
            case PlayerTransform -> {
                PlayerTransform pt = (PlayerTransform) message;
                LOG.info("Player moved to " + pt.pos);
            }
            default -> LOG.info("Unhandled message " + message.getClass().getName());
        }
        Messages.releaseMessage(message);
    }

    public void versionMismatch(short serverVersion) {
        System.out.printf("Server runs version %d, we expect %d.%n", serverVersion, Version);
    }

    public void loginFailed(String message) {
        System.out.printf("Cannot login: %s%n", message);
    }

    protected String chooseCharacter(String[] characters) {
        return characters[0];
    }

    public String chooseServer(String allServers) {
        String[] servers = allServers.split(";");
        return servers[0];
    }

    public static void main(String[] args) {
        Messages.init();
        new GameClient(GameClientConfig.localTestConfig).run();
    }

}
