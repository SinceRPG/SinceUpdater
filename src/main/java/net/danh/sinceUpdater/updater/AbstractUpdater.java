package net.danh.sinceUpdater.updater;

import net.danh.sinceUpdater.SinceUpdater;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public abstract class AbstractUpdater {

    protected final SinceUpdater plugin;
    protected final OkHttpClient client;
    protected final File pluginsFolder;
    protected final File updateFolder;
    protected final String logPrefix;
    private final String configPathEnabled;
    private final String configPathInterval;

    public AbstractUpdater(SinceUpdater plugin, String configPathEnabled, String configPathInterval, String logPrefix) {
        this.plugin = plugin;
        this.client = new OkHttpClient();
        this.pluginsFolder = plugin.getDataFolder().getParentFile();
        this.updateFolder = new File(pluginsFolder, "update");
        this.configPathEnabled = configPathEnabled;
        this.configPathInterval = configPathInterval;
        this.logPrefix = logPrefix;
    }

    public void start() {
        // SỬA ĐỔI: Dùng plugin.getConfigUtils().getConfig() thay vì plugin.getConfig()
        if (!plugin.getConfigUtils().getConfig().getBoolean(configPathEnabled, true)) return;

        long hours = plugin.getConfigUtils().getConfig().getLong(configPathInterval, 6);
        long ticks = hours * 60L * 60L * 20L;

        plugin.getLogger().info(logPrefix + " Active. Checking every " + hours + " hours.");

        if (!updateFolder.exists()) updateFolder.mkdirs();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkForUpdate();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, logPrefix + " Error checking update", e);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, ticks);
    }

    public void triggerUpdate() {
        if (!plugin.getConfigUtils().getConfig().getBoolean(configPathEnabled, true)) return;
        if (!updateFolder.exists()) updateFolder.mkdirs();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    checkForUpdate();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, logPrefix + " Error checking update", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    protected abstract void checkForUpdate();

    protected boolean downloadFile(String url, File destinationFolder, String fileName) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            return MythicUpdater.fileCheck(destinationFolder, fileName, response);
        } catch (IOException e) {
            plugin.getLogger().severe(logPrefix + " Download failed: " + e.getMessage());
            return false;
        }
    }

    protected File findJarInFolder(File folder, String startWith) {
        if (!folder.exists()) return null;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(startWith) && file.getName().endsWith(".jar")) {
                    return file;
                }
            }
        }
        return null;
    }
}