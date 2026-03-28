package net.danh.sinceUpdater.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.danh.sinceUpdater.SinceUpdater;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

public class EssentialsUpdater extends AbstractUpdater {

    private static final String JENKINS_URL = "https://ci.ender.zone/job/EssentialsX/lastSuccessfulBuild/api/json";

    public EssentialsUpdater(SinceUpdater plugin) {
        super(plugin, "essentials-updater.enabled", "essentials-updater.check-interval", "[Ess-Updater]");
    }

    @Override
    protected void checkForUpdate() {
        Request request = new Request.Builder().url(JENKINS_URL).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            String jsonString = response.body().string();
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();

            if (!root.has("artifacts")) return;
            JsonArray artifacts = root.getAsJsonArray("artifacts");

            ConfigurationSection modulesConfig = plugin.getConfigUtils().getConfig().getConfigurationSection("essentials-updater.modules");
            if (modulesConfig == null || artifacts.size() == 0) return;

            for (JsonElement element : artifacts) {
                JsonObject artifact = element.getAsJsonObject();
                String fileName = artifact.get("fileName").getAsString();
                String moduleName = getModuleName(fileName);

                if (moduleName != null && modulesConfig.getBoolean(moduleName, false)) {
                    processModule(moduleName, fileName, artifact.get("relativePath").getAsString());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processModule(String moduleName, String newFileName, String relativePath) {
        File currentRunning = findModuleJar(pluginsFolder, moduleName);
        if (currentRunning != null && currentRunning.getName().equals(newFileName)) return;

        File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

        File pendingFile = findModuleJar(outputFolder, moduleName);
        if (pendingFile != null) {
            if (pendingFile.getName().equals(newFileName)) return;
            pendingFile.delete();
        }

        plugin.getLogger().info("[Ess-Updater] Found new version for " + moduleName + ": " + newFileName);
        String url = "https://ci.ender.zone/job/EssentialsX/lastSuccessfulBuild/artifact/" + relativePath;

        if (downloadFile(url, outputFolder, newFileName)) {
            plugin.getLogger().info("[Ess-Updater] Successfully downloaded " + newFileName + " to '" + outputFolder.getName() + "' folder.");
        }
    }

    private String getModuleName(String fileName) {
        if (fileName == null || !fileName.startsWith("EssentialsX")) return null;
        int dashIndex = fileName.indexOf("-");
        return (dashIndex != -1) ? fileName.substring(0, dashIndex) : fileName.replace(".jar", "");
    }

    private File findModuleJar(File folder, String moduleName) {
        if (!folder.exists()) return null;

        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".jar")) continue;

                String localModuleName = getModuleName(file.getName());
                if (localModuleName == null) continue;

                if (localModuleName.equals(moduleName)) {
                    return file;
                }
            }
        }
        return null;
    }
}