package net.nothingtv.game.network.server;

import net.nothingtv.game.network.MessageHandler;
import net.nothingtv.game.network.NetworkReader;
import net.nothingtv.game.network.NetworkWriter;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.MessageRegister;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.message.impl.GameServerConnect;
import net.nothingtv.game.network.message.impl.GameServerReady;
import net.nothingtv.game.network.message.impl.LoginServerConnect;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This handler consists of a read and writer for LS - GS communication and is handled by the login server
 * which initiates the connection channel
 */
public class GameServerConnection implements Runnable, MessageHandler {

    private static final Logger LOG = Logger.getLogger(GameServerConnection.class.getName());

    private final LoginServer loginServer;
    private final String host;
    private final int port;
    private NetworkWriter writer;
    private SocketChannel socket;

    public GameServerConnection(LoginServer loginServer, String host, int port) {
        this.loginServer = loginServer;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("LS -> GS connection");
        LOG.info("LS: contacting the game server at " + host + ":" + port);
        try {
            socket = SocketChannel.open();
            if (socket.connect(new InetSocketAddress(host, port)))
                socket.finishConnect();
            new Thread(new NetworkReader(socket, this)).start();
            new Thread(writer = new NetworkWriter(socket)).start();
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LS: Cannot connect to " + host + " at " + port);
            throw new RuntimeException(e);
        }
        LoginServerConnect lsc = Messages.obtain(LoginServerConnect.class);
        lsc.token = loginServer.getCommToken();
        writer.send(lsc);
        while (socket.isOpen()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        LOG.info("LS: Game server connection stopped");
    }

    @Override
    public void handleMessage(Message message) {
        MessageRegister.MessageId id = MessageRegister.MessageId.values[message.getMessageId()];
        switch (id) {
            case GameServerConnect -> {
                GameServerConnect reply = (GameServerConnect) message;
                if (!loginServer.checkGSToken(reply.token))
                    quickClose();
            }
            case GameServerReady -> {
                GameServerReady reply = (GameServerReady) message;
                loginServer.addExpectedUser(reply.user);
            }
            default -> LOG.info("Unhandled message " + message.getClass().getName());
        }
    }

    public void send(Message message) {
        writer.send(message);
    }

    private void quickClose() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
