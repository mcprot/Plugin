package tachyon.plugin;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import tachyon.plugin.utils.ReflectionUtils;
import tachyon.plugin.utils.Signing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bungee extends Plugin implements Listener {
    private boolean onlyProxy;
    private boolean debugMode;

    public void onEnable() {
        saveDefaultConfig();
        Configuration config = getConfig();

        try {
            Signing.init();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            getLogger().severe("Couldn't initialize signing module.");
            throw new RuntimeException(e);
        }

        onlyProxy = config.getBoolean("only-allow-proxy-connections");
        debugMode = config.getBoolean("debug-mode");

        getProxy().getPluginManager().registerListener(this, this);

        Logger logger = ProxyServer.getInstance().getLogger();
        Logger newLogger = new Logger("BungeeCord", null) {
            public void log(Level level, String msg, Object param1) {
                if ((msg.equals("{0} has connected")) && (param1.getClass().getSimpleName().equals("InitialHandler"))) {
                    return;
                }
                super.log(level, msg, param1);
            }
        };
        newLogger.setParent(logger);
        try {
            ReflectionUtils.setFinalField(ProxyServer.getInstance().getClass(), ProxyServer.getInstance(), "logger", newLogger);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Configuration getConfig() {
        try {
            return ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            throw new RuntimeException("Couldn't load config", e);
        }
    }

    private void saveDefaultConfig() {
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        File file = new File(getDataFolder(), "config.yml");

        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler(priority = -64)
    public void onProxyPingEvent(ProxyPingEvent event) {
        if (event.getConnection().isLegacy()) {
            try {
                Object ch = ReflectionUtils.getPrivateField(event.getConnection().getClass(), event.getConnection(), Object.class, "ch");
                Method m = ch.getClass().getMethod("close");
                m.invoke(ch);
            } catch (Exception e) {
                event.getConnection().disconnect();
            }
        }
    }

    @EventHandler(priority = -64)
    public void onPlayerHandshake(PlayerHandshakeEvent event) {
        boolean proxyConnection = false;
        Channel channel = null;
        PendingConnection connection = event.getConnection();

        try {
            Object ch = ReflectionUtils.getPrivateField(connection.getClass(), connection, Object.class, "ch");
            Method method = ch.getClass().getDeclaredMethod("getHandle");
            channel = (Channel) method.invoke(ch, new Object[0]);

            if (event.getHandshake().getHost().contains("//")) {
                String raw = event.getHandshake().getHost();

                String[] payload = raw.split("///", 4);
                if (payload.length >= 4) {
                    String hostname = payload[0];
                    String ipData = payload[1];
                    int timestamp = Integer.parseInt(payload[2]);
                    String signature = payload[3];

                    String[] hostnameParts = ipData.split(":");
                    String host = hostnameParts[0];
                    int port = Integer.parseInt(hostnameParts[1]);

                    String reconstructedPayload = hostname + "///" + host + ":" + port + "///" + timestamp;

                    if (signature.contains("%%%")) {
                        signature = signature.split("%%%", 2)[0];
                    }

                    if (!Signing.verify(reconstructedPayload.getBytes(StandardCharsets.UTF_8), signature)) {
                        throw new Exception("Couldn't verify signature.");
                    }

                    long currentTime = System.currentTimeMillis() / 1000;

                    if (!(timestamp >= (currentTime - 2) && timestamp <= (currentTime + 2))) {
                        if (debugMode) {
                            getLogger().warning("Current time: " + currentTime + ", Timestamp Time: " + timestamp);
                        }
                        throw new Exception("Invalid signature timestamp, please check system's local clock if error persists.");
                    }
                    proxyConnection = true;

                    InetSocketAddress sockadd = new InetSocketAddress(host, port);
                    ReflectionUtils.setFinalField(ch.getClass(), ch, "remoteAddress", sockadd);
                    ReflectionUtils.setFinalField(AbstractChannel.class, channel, "remoteAddress", sockadd);
                    ReflectionUtils.setFinalField(AbstractChannel.class, channel, "localAddress", sockadd);

                    InetSocketAddress virtualHost = new InetSocketAddress(hostname, event.getHandshake().getPort());
                    try {
                        ReflectionUtils.setFinalField(connection.getClass(), connection, "virtualHost", virtualHost);
                    } catch (Exception ex) {
                        ReflectionUtils.setFinalField(connection.getClass(), connection, "vHost", virtualHost);
                    }
                    ReflectionUtils.setFinalField(event.getHandshake().getClass(), event.getHandshake(), "host", hostname);
                }
            }
        } catch (Exception localException) {
            localException.printStackTrace();
        } finally {
            if (onlyProxy && !proxyConnection) {
                if (debugMode) {
                    getLogger().warning("Disconnecting " + connection.getAddress() + " because no proxy info was received and only-allow-proxy-connections is enabled.");
                }

                if (channel != null) {
                    channel.flush().close();
                } else {
                    connection.disconnect();
                }
            }
        }
    }
}