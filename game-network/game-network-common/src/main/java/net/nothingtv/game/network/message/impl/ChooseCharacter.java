package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class ChooseCharacter extends Message {
    public String character;

    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        character = getString(buffer);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putString(buffer, character);
    }

    @Override
    public short getMessageId() {
        return 11;
    }

    @Override
    public void released() {
        character = "";
    }
}
