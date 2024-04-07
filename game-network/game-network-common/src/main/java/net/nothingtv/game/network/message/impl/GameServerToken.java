package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class GameServerToken extends Message {

    public int token;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        token = buffer.getInt();
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        buffer.putInt(token);
    }

    @Override
    public short getMessageId() {
        return 5;
    }

    @Override
    public void released() {
        token = 0;
    }
}
