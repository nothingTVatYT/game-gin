package net.nothingtv.game.network.message;

// THIS FILE IS GENERATED - DO NOT EDIT - RUN CodeGen.main()

import net.nothingtv.game.network.message.impl.*;

public class MessageRegister {
    public enum MessageId {
        None, //0
        LoginServerGreeting, //1
        LoginRequest, //2
        LoginReply, //3
        ChooseServerRequest, //4
        GameServerToken, //5
        AnnounceUser, //6
        GameServerReady, //7
        GameServerGreeting, //8
        GameServerLoginRequest, //9
        GameServerLoginReply, //10
        ChooseCharacter, //11
        LoginServerConnect, //12
        GameServerConnect, //13
        PlayerTransform; //14

        public static final MessageId[] values = values();
    }

    public static void register() {
        Messages.registerMessage(1, LoginServerGreeting.class);
        Messages.registerMessage(2, LoginRequest.class);
        Messages.registerMessage(3, LoginReply.class);
        Messages.registerMessage(4, ChooseServerRequest.class);
        Messages.registerMessage(5, GameServerToken.class);
        Messages.registerMessage(6, AnnounceUser.class);
        Messages.registerMessage(7, GameServerReady.class);
        Messages.registerMessage(8, GameServerGreeting.class);
        Messages.registerMessage(9, GameServerLoginRequest.class);
        Messages.registerMessage(10, GameServerLoginReply.class);
        Messages.registerMessage(11, ChooseCharacter.class);
        Messages.registerMessage(12, LoginServerConnect.class);
        Messages.registerMessage(13, GameServerConnect.class);
        Messages.registerMessage(14, PlayerTransform.class);
    }
}
