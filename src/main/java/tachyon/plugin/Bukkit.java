package tachyon.plugin;

import com.comphenix.protocol.ProtocolLibrary;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import tachyon.plugin.utils.Signing;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Bukkit extends JavaPlugin implements Listener {
    private static Bukkit INSTANCE;
    private static boolean stopping = false;

    public void onEnable() {
        INSTANCE = this;

        try {
            Signing.init();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            getLogger().severe("Couldn't initialize signing module.");
            throw new RuntimeException(e);
        }

        saveDefaultConfig();
        FileConfiguration config = getConfig();
        boolean onlyProxy = config.getBoolean("only-allow-proxy-connections");
        boolean debugMode = config.getBoolean("debug-mode");

        ProtocolLibrary.getProtocolManager().addPacketListener(new HandshakePacketHandler(getLogger(), onlyProxy, debugMode));

    }


    public void onDisable() {
        stopping = true;
        INSTANCE = null;
    }

    public static Bukkit getInstance() {
        if (stopping) {
            throw new IllegalAccessError("Plugin is disabling!");
        }
        return INSTANCE;
    }
}