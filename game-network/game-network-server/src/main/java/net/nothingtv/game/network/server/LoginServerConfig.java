package net.nothingtv.game.network.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class LoginServerConfig {
    private static final String DefaultConfigFile = "loginserver.config";
    public String loginServer;
    public String gameServersCoded;
    public String[] gameServers;
    public String[] gameServersInternal;
    public int[] gameServerCommPorts;
    public int[] gameServerPorts;
    public int loginPort;

    public LoginServerConfig() {
        loadConfig();
    }

    protected void loadConfig() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream(DefaultConfigFile)) {
            properties.load(fis);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        loginServer = properties.getProperty("loginServer", "localhost");
        loginPort = Integer.parseInt(properties.getProperty("loginPort", "4781"));

        int knownGameServers = Integer.parseInt(properties.getProperty("numGameServers", "0"));
        gameServers = new String[knownGameServers];
        gameServerPorts = new int[knownGameServers];
        gameServersInternal = new String[knownGameServers];
        gameServerCommPorts = new int[knownGameServers];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < knownGameServers; i++) {
            gameServers[i] = properties.getProperty("gameServer" + i, "localhost");
            gameServersInternal[i] = properties.getProperty("gameServerInternal" + i, gameServers[i]);
            gameServerPorts[i] = Integer.parseInt(properties.getProperty("gameServerPort" + i, "4782"));
            gameServerCommPorts[i] = Integer.parseInt(properties.getProperty("gameServerCommPort" + i, "4783"));
            if (!sb.isEmpty())
                sb.append(";");
            sb.append(gameServers[i]).append(':').append(gameServerPorts[i]);
        }
        gameServersCoded = sb.toString();
    }
}
