package net.nothingtv.game.network.server;

import com.fasterxml.jackson.jr.ob.JSON;
import net.nothingtv.game.network.Tools;
import net.nothingtv.game.network.data.Player;
import net.nothingtv.game.storage.client.Client;
import net.nothingtv.game.storage.common.StorageConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBClient {
    private static final Logger LOG = Logger.getLogger(DBClient.class.getName());
    protected static final String Table_User = "user";
    protected static final String Table_PlayerNames = "playerNames";
    protected static final String Table_Objects = "objects";

    protected final Client client;

    public DBClient() {
        StorageConfig storageConfig = new StorageConfig();
        client = new Client(storageConfig);
    }

    public byte[] get(String columnFamily, String key) throws IOException {
        return client.get(columnFamily, key.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] get(String columnFamily, int id) throws IOException {
        return client.get(columnFamily, Tools.toBytes(id));
    }

    public int getInt(String columnFamily, String key, int defaultValue) throws IOException {
        byte[] result = client.get(columnFamily, key.getBytes(StandardCharsets.UTF_8));
        if (result != null) {
            return Integer.parseInt(new String(result).strip());
        }
        return defaultValue;
    }

    public void set(String columnFamily, String key, String value) throws IOException {
        client.update(columnFamily, key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
    }

    public boolean checkUserPassword(String user, String password) {
        try {
            byte[] pw = get(Table_User, user);
            return pw != null && password.equals(new String(pw, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot access user table", e);
        }
        return false;
    }

    public boolean addUser(String user, String password) {
        try {
            byte[] pw = get(Table_User, user);
            if (pw != null) return false;
            set(Table_User, user, password);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot add user", e);
        }
        return false;
    }

    public boolean updateUser(String user, String password) {
        try {
            byte[] pw = get(Table_User, user);
            if (pw == null) return false;
            set(Table_User, user, password);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot update user", e);
        }
        return false;
    }

    public void updatePlayer(Player player) throws IOException {
        client.update(Table_Objects, Tools.toBytes(player.id), JSON.std.asBytes(player));
    }

    public void addPlayer(Player player) throws IOException {
        player.id = client.uid();
        updatePlayer(player);
        client.update(Table_PlayerNames, loginPlayerKey(player).getBytes(StandardCharsets.UTF_8), Tools.toBytes(player.id));
    }

    public Player loadPlayer(Player player) throws IOException {
        if (player.id == 0) {
            player.id = getInt(Table_PlayerNames, loginPlayerKey(player), 0);
            if (player.id == 0)
                return null;
        }
        byte[] res = get(Table_Objects, player.id);
        Player loadedPlayer = JSON.std.beanFrom(Player.class, res);
        return loadedPlayer;
    }

    public String loginPlayerKey(Player player) {
        return loginPlayerKey(player.login, player.name);
    }

    public String loginPlayerKey(String login, String player) {
        return login + "\t" + player;
    }

    public void close() {
        client.close();
    }
}
