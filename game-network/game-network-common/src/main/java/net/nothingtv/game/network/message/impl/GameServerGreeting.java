package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class GameServerGreeting extends Message {
    public short version;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        version = buffer.getShort();
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        buffer.putShort(version);
    }

    @Override
    public short getMessageId() {
        return 8;
    }

    @Override
    public void released() {

    }
}
