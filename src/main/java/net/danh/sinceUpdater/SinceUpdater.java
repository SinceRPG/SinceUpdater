package net.danh.sinceUpdater;

import net.danh.sinceUpdater.updater.*;
import net.danh.sinceUpdater.utils.ConfigUtils;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
public final class SinceUpdater extends JavaPlugin {

    private static SinceUpdater instance;
    private ConfigUtils configUtils;
    private ConfigUtils dataUtils; // Thêm dataUtils để lưu lịch sử bản build của Mythic
    private final List<AbstractUpdater> updaters = new ArrayList<>();

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

        // Đăng ký lệnh
        getCommand("sinceupdater").setExecutor(new net.danh.sinceUpdater.command.SinceUpdaterCommand(this));

        getLogger().info("SinceUpdater has been enabled!");
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(this);
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
}