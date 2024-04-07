package net.nothingtv.game.network.message.impl;

import net.nothingtv.game.network.NVector3;
import net.nothingtv.game.network.message.Message;

import java.nio.ByteBuffer;

public class PlayerTransform extends Message {
    public final NVector3 pos = new NVector3();
    public NVector3 direction = new NVector3();
    @Override
    public void readContent(ByteBuffer buffer, short remaining) {
        getVector3(buffer, pos);
        getVector3(buffer, direction);
    }

    @Override
    public void writeContent(ByteBuffer buffer) {
        putVector3(buffer, pos);
        putVector3(buffer, direction);
    }

    @Override
    public short getMessageId() {
        return 14;
    }

    @Override
    public void released() {
    }
}
