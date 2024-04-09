package net.nothingtv.game.network.server;

import net.nothingtv.game.network.MessageHandler;
import net.nothingtv.game.network.NetworkReader;
import net.nothingtv.game.network.NetworkWriter;
import net.nothingtv.game.network.Tools;
import net.nothingtv.game.network.data.Player;
import net.nothingtv.game.network.message.Message;
import net.nothingtv.game.network.message.MessageRegister;
import net.nothingtv.game.network.message.Messages;
import net.nothingtv.game.network.message.impl.*;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.logging.Logger;

/**
 * This session handler is controlled by the GS and communicates with the user
 */
public class GameSession implements Runnable, MessageHandler {

    private static final Logger LOG = Logger.getLogger(GameSession.class.getName());

    private final SocketChannel socket;
    private final GameServer gameServer;
    private NetworkWriter writer;
    private boolean loggedIn;
    private String login;
    private Player player;
    private int playerId;

    public GameSession(GameServer gameServer, SocketChannel socket) {
        this.gameServer = gameServer;
        this.socket = socket;
        loggedIn = false;
    }

    @Override
    public void run() {
        LOG.info("Game session started");
        Thread.currentThread().setName("GS user session");
        new Thread(new NetworkReader(socket, this)).start();
        new Thread(writer = new NetworkWriter(socket)).start();
        GameServerGreeting greetings = Messages.obtain(GameServerGreeting.class);
        greetings.version = GameServer.Version;
        writer.send(greetings);

        long started = System.currentTimeMillis();
        while (socket.isOpen()) {
            Tools.nap(1000);
            if (login == null || login.isEmpty()) {
                if (System.currentTimeMillis() - started > 2000) {
                    LOG.info("GS: Timeout in login");
                    break;
                }
                // send the greeting again
                greetings = Messages.obtain(GameServerGreeting.class);
                greetings.version = GameServer.Version;
                writer.send(greetings);
            }
        }
        LOG.info("Game session stopped");
    }

    public void handleMessage(Message message) {
        MessageRegister.MessageId id = MessageRegister.MessageId.values[message.getMessageId()];
        switch(id) {
            case GameServerLoginRequest -> {
                GameServerLoginRequest lr = (GameServerLoginRequest) message;
                GameServerLoginReply reply = Messages.obtain(GameServerLoginReply.class);
                if (gameServer.authenticate(lr.login, lr.token)) {
                    login = lr.login;
                    loggedIn = true;
                    reply.accepted = true;
                    reply.characters = getCharacters();
                } else {
                    reply.accepted = false;
                    loggedIn = false;
                }
                writer.send(reply);
            }
            case ChooseCharacter -> {
                if (loggedIn) {
                    ChooseCharacter cc = (ChooseCharacter) message;
                    player = gameServer.getPlayer(login, cc.character);
                    player.networkWriter = writer;
                    playerId = player.id;
                    PlayerTransform reply = Messages.obtain(PlayerTransform.class);
                    reply.pos.set(player.pos);
                    reply.direction.set(player.direction);
                    writer.send(reply);
                }
            }
            default -> LOG.info("Unhandled message " + message.getClass().getName());
        }
        Messages.releaseMessage(message);
    }

    private String[] getCharacters() {
        return new String[] { "Player#1" };
    }
}
