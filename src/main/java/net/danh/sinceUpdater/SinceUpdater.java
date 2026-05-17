package net.danh.sinceUpdater;

import net.danh.sinceUpdater.command.SinceUpdaterCommand;
import net.danh.sinceUpdater.updater.*;
import net.danh.sinceUpdater.utils.ConfigUtils;
import okhttp3.OkHttpClient;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class SinceUpdater extends JavaPlugin {

    private static SinceUpdater instance;
    private ConfigUtils configUtils;
    private ConfigUtils dataUtils;
    private final List<AbstractUpdater> updaters = new ArrayList<>();

    // Shared instances to prevent memory leaks and provide Folia compatibility
    private ScheduledExecutorService scheduler;
    private OkHttpClient httpClient;

    public static SinceUpdater getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configUtils = new ConfigUtils(this, "config.yml");
        dataUtils = new ConfigUtils(this, "data.yml");

        // Initialize a single thread pool for async tasks (Folia compatible)
        scheduler = Executors.newScheduledThreadPool(2);

        // Initialize a singleton OkHttpClient to prevent thread & connection leaks
        httpClient = new OkHttpClient();

        // Load Updaters
        loadUpdaters();

        // Register Commands
        getCommand("sinceupdater").setExecutor(new SinceUpdaterCommand(this));

        getLogger().info("SinceUpdater has been enabled with Folia support!");
    }

    @Override
    public void onDisable() {
        // Shutdown scheduler safely
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        // Evict OkHttpClient connection pools and shutdown its executor to prevent memory leaks
        if (httpClient != null) {
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }

        getLogger().info("SinceUpdater has been disabled!");
    }

    private void loadUpdaters() {
        updaters.add(new CitizensUpdater(this));
        updaters.add(new EssentialsUpdater(this));
        updaters.add(new FaweUpdater(this));
        updaters.add(new MythicUpdater(this));
        updaters.add(new PhoenixUpdater(this));

        for (AbstractUpdater updater : updaters) {
            updater.start();
        }
    }

    public List<AbstractUpdater> getUpdaters() {
        return updaters;
    }

    public ConfigUtils getConfigUtils() {
        return configUtils;
    }

    public ConfigUtils getDataUtils() {
        return dataUtils;
    }

    public ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }
}