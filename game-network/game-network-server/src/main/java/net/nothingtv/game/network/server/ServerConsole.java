package net.nothingtv.game.network.server;

import bsh.CommandLineReader;
import bsh.EvalError;
import bsh.FileReader;
import bsh.Interpreter;
import net.nothingtv.game.network.message.Messages;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;

public class ServerConsole {

    private boolean sourceScripts;
    private String startupScript;
    private final String shutdownScript;
    private Interpreter interpreter;
    public LoginServer loginServer;
    public GameServer gameServer;
    private final Random rnd = new Random();

    public ServerConsole(String[] args) {
        sourceScripts = true;
        startupScript = "startup.bsh";
        shutdownScript = "shutdown.bsh";
        int i = 0;
        while (i < args.length) {
            if ("--no-rc".equals(args[i])) {
                sourceScripts = false;
            } else if ("--rc".equals(args[i]) && (i+1) < args.length) {
                startupScript = args[++i];
            }
            i++;
        }
    }

    public void run() {
        Messages.init();
        startConsole();
    }

    public void shutdown() {
        if (sourceScripts) {
            try {
                if (Files.exists(Paths.get(shutdownScript)))
                    interpreter.source(shutdownScript);
            } catch (Exception e) {
                System.err.println("evaluating " + shutdownScript + " failed: " + e);
            }
        }
        System.exit(0);
    }

    public void setCommTokens() {
        int token = rnd.nextInt(1, Integer.MAX_VALUE >> 1);
        String tokenStr = String.format("%d", token);
        try {
            Files.writeString(Paths.get("comm-token"), tokenStr, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(Paths.get("gs-token"), tokenStr, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Cannot create tokens: " + e);
        }
    }

    public void startLoginServer() {
        // for the moment only one LS is allowed
        if (loginServer != null) {
            System.err.println("Login server is already created.");
            return;
        }
        loginServer = new LoginServer();
        new Thread(() -> loginServer.start()).start();
    }

    public void startGameServer() {
        // for the moment only one GS is allowed
        if (gameServer != null) {
            System.err.println("Game server is already created.");
            return;
        }
        gameServer = new GameServer();
        new Thread(() -> gameServer.start()).start();
    }

    public void startConsole() {
        try (FileReader readr = new FileReader(System.in);
            Reader repl = new CommandLineReader(readr)) {
            interpreter = new Interpreter(repl, System.out, System.err, true);
            interpreter.setExitOnEOF(false);
            interpreter.set("server", this);
            if (sourceScripts) {
                try {
                    interpreter.source(startupScript);
                } catch (Exception e) {
                    System.err.println("evaluating " + startupScript + " failed: " + e);
                }
            }
            interpreter.run();
        } catch (IOException|EvalError e) {
            System.err.println("I/O Error on console: " + e);
        }
    }

    public void threads() {
        Thread[] liveThreads = new Thread[Thread.activeCount()];
        Thread.enumerate(liveThreads);
        for (Thread thr : liveThreads) {
            StackTraceElement[] st = thr.getStackTrace();
            String function = "";
            if (st.length > 0)
                function = st[0].getClassName() + "." + st[0].getMethodName() + ":" + st[0].getLineNumber();
            interpreter.println(String.format("#%d '%s' %s (%s)", thr.threadId(), thr.getName(), thr.getState(), function));
        }
    }

    public static void main(String[] args) {
        new ServerConsole(args).run();
    }
}
