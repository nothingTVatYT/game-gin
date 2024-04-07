package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class GameServerLoginReply extends Message {
    public boolean accepted;
    public String[] characters;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        accepted = getBoolean(buffer);
        characters = getStringArray(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putBoolean(buffer, accepted);
        putStringArray(buffer, characters);
    }

    @Override
    public short getMessageId() {
        return 10;
    }

    @Override
    public void released() {
        characters = null;
    }
}
