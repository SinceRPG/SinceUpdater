package net.danh.sinceUpdater.command;

import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.updater.AbstractUpdater;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class SinceUpdaterCommand implements CommandExecutor, TabCompleter {

    private final SinceUpdater plugin;

    public SinceUpdaterCommand(SinceUpdater plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sinceupdater.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("check")) {
            sender.sendMessage(ChatColor.GREEN + "Manually triggering update checks...");
            for (AbstractUpdater updater : plugin.getUpdaters()) {
                updater.triggerUpdate();
            }
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /sinceupdater check");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("sinceupdater.admin")) {
            if ("check".startsWith(args[0].toLowerCase())) {
                completions.add("check");
            }
        }
        return completions;
    }
}
