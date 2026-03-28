package net.danh.sinceUpdater;

import net.danh.sinceUpdater.updater.*;
import net.danh.sinceUpdater.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;

public final class SinceUpdater extends JavaPlugin {

    private static SinceUpdater instance;
    private ConfigUtils configUtils;
    private ConfigUtils dataUtils; // Thêm dataUtils để lưu lịch sử bản build của Mythic

    public static SinceUpdater getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        configUtils = new ConfigUtils(this, "config.yml");
        dataUtils = new ConfigUtils(this, "data.yml");

        // Khởi chạy các Updater
        loadUpdaters();

        getLogger().info("SinceUpdater has been enabled!");
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(this);
        getLogger().info("SinceUpdater has been disabled!");
    }

    private void loadUpdaters() {
        new CitizensUpdater(this).start();
        new EssentialsUpdater(this).start();
        new FaweUpdater(this).start();
        new MythicUpdater(this).start();
        new PhoenixUpdater(this).start();
    }

    public ConfigUtils getConfigUtils() {
        return configUtils;
    }

    public ConfigUtils getDataUtils() {
        return dataUtils;
    }
}