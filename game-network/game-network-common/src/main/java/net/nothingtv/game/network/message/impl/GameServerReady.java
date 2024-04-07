package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class GameServerReady extends Message {
    public String user;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        user = getString(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putString(buffer, user);
    }

    @Override
    public short getMessageId() {
        return 7;
    }

    @Override
    public void released() {
        user = "";
    }
}
