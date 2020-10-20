package com.mcprot.plugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.server.SocketInjector;
import com.comphenix.protocol.injector.server.TemporaryPlayerFactory;
import com.mcprot.plugin.utils.ReflectionUtils;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class HandshakePacketHandler extends PacketAdapter {
    private final Logger logger;
    private final boolean onlyProxy;
    private final boolean debugMode;
    private String properField = null;

    public HandshakePacketHandler(Bukkit instance, Logger logger, boolean onlyProxy, boolean debugMode) {
        super(instance, PacketType.Handshake.Client.SET_PROTOCOL);
        this.logger = logger;
        this.onlyProxy = onlyProxy;
        this.debugMode = debugMode;
    }

    public void onPacketReceiving(PacketEvent event) {
        boolean proxyConnection = false;
        String raw;
        try {
            raw = event.getPacket().getStrings().read(0);
            String extraData = "";
            String[] hostnameSplits = raw.split("\0", 2);
            if (hostnameSplits.length > 1) {
                extraData = hostnameSplits[1];
            }
            raw = hostnameSplits[0];

            String[] payload = raw.split("///", 4);
            if (payload.length >= 4) {
                String hostname = payload[0];
                String ipData = payload[1];
                long timestamp = Long.parseLong(payload[2]);
                String signature = payload[3];

                String[] hostnameParts = ipData.split(":");
                String host = hostnameParts[0];
                int port = Integer.parseInt(hostnameParts[1]);

                String reconstructedPayload = hostname + "///" + host + ":" + port + "///" + timestamp;

                if (!Signing.verify(reconstructedPayload.getBytes(StandardCharsets.UTF_8), signature)) {
                    throw new Exception("Couldn't verify signature.");
                }

                long currentTime = System.currentTimeMillis() / 1000;

                if (!(timestamp >= (currentTime - 300) && timestamp <= (currentTime + 300))) {
                    if (debugMode) {
                        logger.warning("Current time: " + currentTime + ", Timestamp Time: " + timestamp);
                    }
                    //throw new Exception("Invalid signature timestamp, please check system's local clock if error persists.");
                }

                try {
                    SocketInjector ignored = TemporaryPlayerFactory.getInjectorFromPlayer(event.getPlayer());

                    Object injector = ReflectionUtils.getPrivateField(ignored.getClass(), ignored, "injector");
                    Object networkManager = ReflectionUtils.getPrivateField(injector.getClass(), injector, "networkManager");

                    if (properField == null) {
                        properField = ReflectionUtils.getProperField(networkManager.getClass());
                    }

                    Channel channel = (Channel) ReflectionUtils.getPrivateField(injector.getClass(), injector, "originalChannel");
                    InetSocketAddress newRemoteAddress = new InetSocketAddress(host, port);
                    proxyConnection = true;
                    try {
                        ReflectionUtils.setFinalField(networkManager.getClass(), networkManager, this.properField == null ? "l" : this.properField, newRemoteAddress);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    ReflectionUtils.setFinalField(AbstractChannel.class, channel, "remoteAddress", newRemoteAddress);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                hostname = hostname + extraData;
                event.getPacket().getStrings().write(0, hostname);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if ((onlyProxy) && (!proxyConnection)) {
                Player player = event.getPlayer();

                if (debugMode) {
                    logger.warning("Disconnecting " + player.getAddress() + " because no proxy info was received and only-allow-proxy-connections is enabled.");
                }
                player.kickPlayer("");
            }
        }
    }
}
