package net.danh.sinceUpdater.updater;

import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.server.ServerVersion;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitizensUpdater extends AbstractUpdater {

    private final String targetModuleToken;
    private final String baseJobUrl;
    private final int maxSearchDepth;

    public CitizensUpdater(SinceUpdater plugin) {
        super(plugin, "citizens-updater.enabled", "citizens-updater.check-interval", "[Citizens-Updater]");
        this.targetModuleToken = detectTargetModule();
        this.baseJobUrl = plugin.getConfigUtils().getString("urls.citizens.base-job", "https://ci.citizensnpcs.co/job/Citizens2/");
        this.maxSearchDepth = plugin.getConfigUtils().getInt("urls.citizens.max-search-depth", 15);

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
            checkLatestStrategy();
            return;
        }

        String configPath = "citizens-updater.old_build." + targetModuleToken;

        if (plugin.getConfigUtils().getConfig().isSet(configPath)) {
            String configValue = plugin.getConfigUtils().getString(configPath);

            if (!"latest".equalsIgnoreCase(configValue)) {
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

        plugin.getLogger().info("[Citizens-Updater] Checking for latest compatible build for " + targetModuleToken + "...");
        checkLatestStrategy();
    }

    /**
     * STRATEGY 1: Download specific build from config
     */
    private void checkSpecificBuildStrategy(int buildNumber) {
        String url = baseJobUrl + buildNumber + "/";
        tryDownloadFromPage(url, buildNumber);
    }

    /**
     * STRATEGY 2: Find the latest build, rollback if incompatible
     */
    private void checkLatestStrategy() {
        String currentUrl = baseJobUrl + "lastSuccessfulBuild/";
        int currentBuildNumber = -1;

        for (int i = 0; i < maxSearchDepth; i++) {
            Request request = new Request.Builder().url(currentUrl).build();

            try (Response response = plugin.getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return;

                String htmlContent = response.body().string();

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

                if (targetModuleToken != null) {
                    int index = htmlContent.indexOf(targetModuleToken);
                    if (index == -1) {
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
                    processHtmlContent(htmlContent, currentUrl);
                    return;
                } else {
                    if (currentBuildNumber > 0) {
                        currentBuildNumber--;
                        currentUrl = baseJobUrl + currentBuildNumber + "/";
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

    private void tryDownloadFromPage(String url, int buildNumber) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = plugin.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                plugin.getLogger().warning("[Citizens-Updater] Failed to access Build #" + buildNumber);
                return;
            }
            processHtmlContent(response.body().string(), url);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

        File currentRunning = findCitizensJar(pluginsFolder);
        if (currentRunning != null && currentRunning.getName().equals(selectedFileName)) {
            return;
        }

        File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

        File pendingFile = findCitizensJar(outputFolder);
        if (pendingFile != null) {
            if (pendingFile.getName().equals(selectedFileName)) return;
            pendingFile.delete();
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