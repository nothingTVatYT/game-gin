package net.nothingtv.game.network.message;

import net.nothingtv.game.network.message.impl.ChooseServerRequest;
import net.nothingtv.game.network.message.impl.LoginReply;
import net.nothingtv.game.network.message.impl.LoginRequest;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Messages {
    private static final Logger LOG = Logger.getLogger(Messages.class.getName());
    private static final HashMap<Short, Class<? extends Message>> messageTypes = new HashMap<>();
    private static final HashMap<Class<? extends Message>, LinkedList<? extends Message>> pool = new HashMap<>();

    public static final int MinSize = 4;

    public static void init() {
        MessageRegister.register();
    }

    public static void registerMessage(int type, Class<? extends Message> messageClass) {
        registerMessage((short)type, messageClass);
    }

    public static void registerMessage(short type, Class<? extends Message> messageClass) {
        if (messageTypes.containsKey(type)) {
            LOG.severe("Cannot register " + messageClass + ": There is already a class registered for id " + type + ": " + messageTypes.get(type));
            return;
        }
        messageTypes.put(type, messageClass);
    }

    public static short registerMessage(Message message) {
        short id = message.getMessageId();
        registerMessage(id, message.getClass());
        return id;
    }

    public static <T extends Message> T obtain(short messageType) {
        return obtain((Class<T>)messageTypes.get(messageType));
    }

    public static <T extends Message> T obtain(Class<T> messageClass) {
        T result;
        try {
            result = (T) pool.get(messageClass).pop();
        } catch (Exception e) {
            result = null;
        }
        if (result == null)
            result = (T)createMessage(messageClass);
        return result;
    }

    private static Message createMessage(Class<? extends Message> messageClass) {
        try {
            return messageClass.getConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            LOG.log(Level.WARNING, "Cannot create a message for type " + messageClass, e);
        }
        return null;
    }

    public static <T extends Message> void releaseMessage(T message) {
        message.released();
        LinkedList<T> objectsList = (LinkedList<T>)pool.get(message.getClass());
        if (objectsList == null) {
            LinkedList<T> list = new LinkedList<>();
            list.add(message);
            synchronized(pool) {
                pool.put(message.getClass(), list);
            }
        } else {
            synchronized(objectsList) {
                objectsList.add(message);
            }
        }
    }
}
