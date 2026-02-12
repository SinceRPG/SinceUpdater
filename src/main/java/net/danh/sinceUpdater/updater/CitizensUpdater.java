package net.danh.sinceUpdater.updater;

import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.server.ServerVersion;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitizensUpdater extends AbstractUpdater {

    private static final String BASE_JOB_URL = "https://ci.citizensnpcs.co/job/Citizens2/";
    private static final int MAX_SEARCH_DEPTH = 15; // Giới hạn tìm ngược lại cho chế độ 'latest'
    private final String targetModuleToken;

    public CitizensUpdater(SinceUpdater plugin) {
        super(plugin, "citizens-updater.enabled", "citizens-updater.check-interval", "[Citizens-Updater]");
        this.targetModuleToken = detectTargetModule();

        if (this.targetModuleToken != null) {
            plugin.getLogger().info("[Citizens-Updater] Detected NMS module: " + this.targetModuleToken);
        } else {
            plugin.getLogger().warning("[Citizens-Updater] Could not detect NMS module.");
        }
    }

    private String detectTargetModule() {
        try {
            ServerVersion sv = new ServerVersion();
            int[] ver = sv.getBukkitVersion();
            int rev = sv.getRevisionNumber();

            if (ver.length < 2) return null;
            int major = ver[1];

            if (rev > 0) {
                return "citizens-v1_" + major + "_R" + rev;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void checkForUpdate() {
        if (targetModuleToken == null) {
            // Không xác định được version server -> Chạy chế độ latest mặc định
            checkLatestStrategy();
            return;
        }

        // 1. Kiểm tra trong config xem có override build không
        String configPath = "citizens-updater.old_build." + targetModuleToken;

        // Nếu config có set giá trị và giá trị KHÁC "latest"
        if (plugin.getConfig().isSet(configPath)) {
            String configValue = plugin.getConfig().getString(configPath);

            if (!"latest".equalsIgnoreCase(configValue)) {
                // Chế độ: FIXED BUILD (Tải theo số build chỉ định)
                try {
                    int buildNumber = Integer.parseInt(configValue);
                    plugin.getLogger().info("[Citizens-Updater] Configured to use fixed Build #" + buildNumber + " for " + targetModuleToken);
                    checkSpecificBuildStrategy(buildNumber);
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("[Citizens-Updater] Invalid build number in config: " + configValue + ". Falling back to 'latest'.");
                    checkLatestStrategy();
                }
                return;
            }
        }

        // 2. Nếu config là "latest" hoặc không có trong config -> Chạy chế độ tìm kiếm
        plugin.getLogger().info("[Citizens-Updater] Checking for latest compatible build for " + targetModuleToken + "...");
        checkLatestStrategy();
    }

    /**
     * CHIẾN LƯỢC 1: Tải đúng 1 build cụ thể từ config
     */
    private void checkSpecificBuildStrategy(int buildNumber) {
        String url = BASE_JOB_URL + buildNumber + "/";
        tryDownloadFromPage(url, buildNumber, true);
        // true = force download (vì người dùng đã chỉ định rõ build này trong config)
    }

    /**
     * CHIẾN LƯỢC 2: Tìm build mới nhất, lùi lại nếu gặp (didn't run)
     */
    private void checkLatestStrategy() {
        String currentUrl = BASE_JOB_URL + "lastSuccessfulBuild/";
        int currentBuildNumber = -1;

        for (int i = 0; i < MAX_SEARCH_DEPTH; i++) {
            Request request = new Request.Builder().url(currentUrl).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;

                String htmlContent = response.body().string();

                // Lấy số build hiện tại nếu chưa có
                if (currentBuildNumber == -1) {
                    Pattern buildNumPattern = Pattern.compile("Citizens2 #(\\d+)");
                    Matcher buildNumMatcher = buildNumPattern.matcher(htmlContent);
                    if (buildNumMatcher.find()) {
                        try {
                            currentBuildNumber = Integer.parseInt(buildNumMatcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                boolean isSafe = true;

                // Kiểm tra module (didn't run)
                if (targetModuleToken != null) {
                    int index = htmlContent.indexOf(targetModuleToken);
                    if (index == -1) {
                        // Không tìm thấy text module -> Có thể build lỗi hoặc quá cũ
                        isSafe = false;
                    } else {
                        int checkRange = Math.min(htmlContent.length(), index + 400);
                        String statusSnippet = htmlContent.substring(index, checkRange);
                        if (statusSnippet.contains("(didn't run)")) {
                            plugin.getLogger().info("[Citizens-Updater] Build #" + currentBuildNumber + " skipped module " + targetModuleToken + ". Checking previous build...");
                            isSafe = false;
                        }
                    }
                }

                if (isSafe) {
                    // Build OK -> Gọi hàm xử lý tải
                    processHtmlContent(htmlContent, currentUrl);
                    return;
                } else {
                    // Backtrack
                    if (currentBuildNumber > 0) {
                        currentBuildNumber--;
                        currentUrl = BASE_JOB_URL + currentBuildNumber + "/";
                    } else {
                        return;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * Hàm hỗ trợ cho Chiến lược 1: Tải từ URL build cụ thể
     */
    private void tryDownloadFromPage(String url, int buildNumber, boolean force) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                plugin.getLogger().warning("[Citizens-Updater] Failed to access Build #" + buildNumber);
                return;
            }
            processHtmlContent(response.body().string(), url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Logic chung: Parse HTML tìm file .jar và tải xuống
     */
    private void processHtmlContent(String htmlContent, String baseUrl) {
        Pattern pattern = Pattern.compile("Citizens-[0-9.]+-b[0-9]+\\.jar");
        Matcher matcher = pattern.matcher(htmlContent);

        if (!matcher.find()) {
            plugin.getLogger().warning("[Citizens-Updater] Could not find .jar artifact in page: " + baseUrl);
            return;
        }

        String selectedFileName = matcher.group();
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        String downloadUrl = baseUrl + "artifact/dist/target/" + selectedFileName;

        // 1. Check file đang chạy
        File currentRunning = findCitizensJar(pluginsFolder);
        if (currentRunning != null && currentRunning.getName().equals(selectedFileName)) {
            // Đã có file này rồi thì không tải lại
            return;
        }

        // 2. Xác định folder đích
        File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

        // 3. Check file ở folder update (tránh tải lại liên tục nếu chưa restart server)
        File pendingFile = findCitizensJar(outputFolder);
        if (pendingFile != null) {
            if (pendingFile.getName().equals(selectedFileName)) return;
            pendingFile.delete(); // Xóa bản update cũ khác phiên bản
        }

        plugin.getLogger().info("[Citizens-Updater] Downloading: " + selectedFileName);

        if (downloadFile(downloadUrl, outputFolder, selectedFileName)) {
            plugin.getLogger().info("[Citizens-Updater] Saved to " + outputFolder.getName() + " folder. Please restart server to apply.");
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