package net.nothingtv.game.network.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class GameServerConfig {
    private static final String DefaultConfigFile = "gameserver.config";
    public String gameServer;
    public int gamePort;
    public String commServer;
    public int commPort;

    public GameServerConfig() {
        loadConfig();
    }

    protected void loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(DefaultConfigFile)) {
            properties.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        gameServer = properties.getProperty("gameServer", "localhost");
        gamePort = Integer.parseInt(properties.getProperty("gamePort", "4782"));
        commServer = properties.getProperty("commServer", "localhost");
        commPort = Integer.parseInt(properties.getProperty("commPort", "4783"));
    }
}
