package net.danh.sinceUpdater.updater;

import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.server.ServerVersion;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitizensUpdater extends AbstractUpdater {

    private static final String BUILD_PAGE_URL = "https://ci.citizensnpcs.co/job/Citizens2/lastSuccessfulBuild/";
    private final String targetModuleToken;

    public CitizensUpdater(SinceUpdater plugin) {
        super(plugin, "citizens-updater.enabled", "citizens-updater.check-interval", "[Citizens-Updater]");
        this.targetModuleToken = detectTargetModule();

        if (this.targetModuleToken != null) {
            plugin.getLogger().info("[Citizens-Updater] Auto-Strict Mode: Targeting module '" + this.targetModuleToken + "'");
        } else {
            plugin.getLogger().warning("[Citizens-Updater] Could not detect exact NMS module. Strict check disabled.");
        }
    }

    private String detectTargetModule() {
        try {
            // KHÔNG dùng 'new ServerVersion()' nữa
            // Gọi trực tiếp các method static từ class ServerVersion
            int[] ver = ServerVersion.getBukkitVersion();
            int rev = ServerVersion.getRevisionNumber();

            if (ver.length < 2) return null;
            int major = ver[1];

            // Citizens thường dùng format v1_XX_RY cho các bản cũ
            if (rev > 0) {
                return "citizens-v1_" + major + "_R" + rev;
            }

            // Với các bản mới (1.20.5+), Citizens có thể dùng kiến trúc khác
            // hoặc không cần suffix R, trả về null để bỏ qua check strict module
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void checkForUpdate() {
        Request request = new Request.Builder().url(BUILD_PAGE_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            String htmlContent = response.body().string();

            if (targetModuleToken != null) {
                int index = htmlContent.indexOf(targetModuleToken);
                if (index == -1) {
                    plugin.getLogger().warning("[Citizens-Updater] ABORT: Module '" + targetModuleToken + "' not found.");
                    return;
                }
                int checkRange = Math.min(htmlContent.length(), index + 400);
                String statusSnippet = htmlContent.substring(index, checkRange);

                if (statusSnippet.contains("(didn't run)")) {
                    plugin.getLogger().warning("[Citizens-Updater] ABORT: Module '" + targetModuleToken + "' was SKIPPED in this build.");
                    return;
                }
                plugin.getLogger().info("[Citizens-Updater] Module '" + targetModuleToken + "' verified successfully.");
            }

            Pattern pattern = Pattern.compile("Citizens-[0-9.]+-b[0-9]+\\.jar");
            Matcher matcher = pattern.matcher(htmlContent);

            if (!matcher.find()) return;

            String selectedFileName = matcher.group();
            String downloadUrl = BUILD_PAGE_URL + "artifact/dist/target/" + selectedFileName;

            // 1. Check file đang chạy
            File currentRunning = findCitizensJar(pluginsFolder);
            if (currentRunning != null && currentRunning.getName().equals(selectedFileName)) return;

            // 2. Xác định folder đích
            File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

            // 3. Check file ở folder đích
            File pendingFile = findCitizensJar(outputFolder);
            if (pendingFile != null) {
                if (pendingFile.getName().equals(selectedFileName)) return;
                pendingFile.delete();
            }

            plugin.getLogger().info("[Citizens-Updater] Found safe update: " + selectedFileName);

            if (downloadFile(downloadUrl, outputFolder, selectedFileName)) {
                plugin.getLogger().info("[Citizens-Updater] Downloaded to '" + outputFolder.getName() + "' folder.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File findCitizensJar(File folder) {
        if (!folder.exists()) return null;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith("Citizens-") && name.endsWith(".jar")
                        && !name.startsWith("original-")
                        && !name.contains("javadoc")
                        && !name.contains("sources")) {
                    return file;
                }
            }
        }
        return null;
    }
}