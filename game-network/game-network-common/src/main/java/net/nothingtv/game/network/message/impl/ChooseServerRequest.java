package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class ChooseServerRequest extends Message {
    public int serverNumber;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        serverNumber = buffer.getInt();
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        buffer.putInt(serverNumber);
    }

    @Override
    public short getMessageId() {
        return 4;
    }

    @Override
    public void released() {
        serverNumber = 0;
    }
}
