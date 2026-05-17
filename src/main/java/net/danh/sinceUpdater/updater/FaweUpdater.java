package net.danh.sinceUpdater.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.danh.sinceUpdater.SinceUpdater;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;

public class FaweUpdater extends AbstractUpdater {

    private final String jenkinsUrl;
    private final String downloadBase;

    public FaweUpdater(SinceUpdater plugin) {
        super(plugin, "fawe-updater.enabled", "fawe-updater.check-interval", "[FAWE-Updater]");
        this.jenkinsUrl = plugin.getConfigUtils().getString("urls.fawe.jenkins", "https://ci.athion.net/job/FastAsyncWorldEdit/lastSuccessfulBuild/api/json");
        this.downloadBase = plugin.getConfigUtils().getString("urls.fawe.download-base", "https://ci.athion.net/job/FastAsyncWorldEdit/lastSuccessfulBuild/artifact/");
    }

    @Override
    protected void checkForUpdate() {
        Request request = new Request.Builder().url(jenkinsUrl).build();

        try (Response response = plugin.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;

            String jsonString = response.body().string();
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray artifacts = root.getAsJsonArray("artifacts");

            if (artifacts.size() == 0) return;

            String selectedFileName = null;
            String selectedRelativePath = null;

            for (JsonElement element : artifacts) {
                JsonObject artifact = element.getAsJsonObject();
                String fileName = artifact.get("fileName").getAsString();
                if (fileName.contains("CLI")) continue;

                if (fileName.contains("Paper")) {
                    selectedFileName = fileName;
                    selectedRelativePath = artifact.get("relativePath").getAsString();
                    break;
                }
                if (selectedFileName == null && fileName.contains("Bukkit")) {
                    selectedFileName = fileName;
                    selectedRelativePath = artifact.get("relativePath").getAsString();
                }
            }

            if (selectedFileName == null) return;

            File currentRunning = findJarInFolder(pluginsFolder, "FastAsyncWorldEdit");
            if (currentRunning != null && currentRunning.getName().equals(selectedFileName)) return;

            File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;
            File targetFile = findJarInFolder(outputFolder, "FastAsyncWorldEdit");

            if (targetFile != null) {
                if (targetFile.getName().equals(selectedFileName)) return;
                targetFile.delete();
            }

            plugin.getLogger().info("[FAWE-Updater] Found version: " + selectedFileName);
            String url = downloadBase + selectedRelativePath;

            if (downloadFile(url, outputFolder, selectedFileName)) {
                String folderName = outputFolder.getName();
                plugin.getLogger().info("[FAWE-Updater] Downloaded to '" + folderName + "' folder.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}