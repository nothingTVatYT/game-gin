package net.nothingtv.game.network;

import net.nothingtv.game.network.message.Message;

public interface MessageHandler {
    void handleMessage(Message message);
}
