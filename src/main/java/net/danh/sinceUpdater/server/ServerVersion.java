package net.danh.sinceUpdater.server;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerVersion {

    private static final int MAXIMUM_INDEX = 3;
    private static final int[] CURRENT_VERSION;
    private static final int REVISION_NUMBER;
    private static final String CRAFT_BUKKIT_PACKAGE;
    private static final boolean IS_PAPER;

    static {
        final String versionRaw = Bukkit.getBukkitVersion().split("-")[0];
        final String[] versionSplit = versionRaw.split("\\.");

        CURRENT_VERSION = new int[Math.min(MAXIMUM_INDEX, versionSplit.length)];
        for (int i = 0; i < CURRENT_VERSION.length; i++) {
            try {
                CURRENT_VERSION[i] = Integer.parseInt(versionSplit[i]);
            } catch (NumberFormatException e) {
                CURRENT_VERSION[i] = 0;
            }
        }

        boolean paperCheck = false;
        try {
            // Check lớp đặc trưng của Paper
            Class.forName("com.destroystokyo.paper.PaperConfig");
            paperCheck = true;
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
                paperCheck = true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        IS_PAPER = paperCheck;

        int revision = 0;
        String cbPackage = "";

        try {
            final String serverPackageName = Bukkit.getServer().getClass().getPackage().getName();

            String[] split = serverPackageName.split("\\.");

            if (split.length > 3 && split[3].startsWith("v")) {
                cbPackage = split[3];
                Pattern revPattern = Pattern.compile("R(\\d+)");
                Matcher matcher = revPattern.matcher(cbPackage);
                if (matcher.find()) {
                    revision = Integer.parseInt(matcher.group(1));
                }
            } else {
                cbPackage = "v" + CURRENT_VERSION[0] + "_" + CURRENT_VERSION[1] + (CURRENT_VERSION.length > 2 ? "_" + CURRENT_VERSION[2] : "") + "_R0";
                revision = 0;
            }

        } catch (Exception e) {
            cbPackage = "v" + CURRENT_VERSION[0] + "_" + CURRENT_VERSION[1] + "_R0";
        }

        REVISION_NUMBER = revision;
        CRAFT_BUKKIT_PACKAGE = cbPackage;
    }

    private ServerVersion() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static boolean isPaper() {
        return IS_PAPER;
    }

    public static boolean isAbove(int... version) {
        Validate.isTrue(version.length >= 1 && version.length <= MAXIMUM_INDEX,
                "Provide at least 1 integer and at most " + MAXIMUM_INDEX);

        final int maxLength = Math.min(MAXIMUM_INDEX, Math.max(version.length, CURRENT_VERSION.length));

        for (int i = 0; i < maxLength; i++) {
            final int serverVer = i < CURRENT_VERSION.length ? CURRENT_VERSION[i] : 0;
            final int targetVer = i < version.length ? version[i] : 0;

            if (serverVer != targetVer) {
                return serverVer > targetVer;
            }
        }

        return true;
    }

    public static boolean isUnder(int... version) {
        return !isAbove(version);
    }

    public static int getRevisionNumber() {
        return REVISION_NUMBER;
    }

    public static int[] getBukkitVersion() {
        return CURRENT_VERSION;
    }

    @NotNull
    public static String getCraftBukkitVersion() {
        return CRAFT_BUKKIT_PACKAGE;
    }

    @Override
    public String toString() {
        return "ServerVersion{" +
                "package='" + CRAFT_BUKKIT_PACKAGE + '\'' +
                ", revision=" + REVISION_NUMBER +
                ", version=" + Arrays.toString(CURRENT_VERSION) +
                ", isPaper=" + IS_PAPER +
                '}';
    }
}