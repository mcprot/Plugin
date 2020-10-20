package com.mcprot.plugin;

import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Bukkit extends JavaPlugin implements Listener {
    public void onEnable() {
        try {
            Signing.init();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException exception) {
            getLogger().severe("Couldn't initialize signing module.");
            throw new RuntimeException(exception);
        }

        saveDefaultConfig();
        FileConfiguration config = getConfig();
        boolean onlyProxy = config.getBoolean("only-allow-proxy-connections");
        boolean debugMode = config.getBoolean("debug-mode");

        ProtocolLibrary.getProtocolManager().addPacketListener(new HandshakePacketHandler(this, getLogger(), onlyProxy, debugMode));
    }

    public void onDisable() {
    }
}