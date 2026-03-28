package net.danh.sinceUpdater.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.utils.ConfigUtils;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.time.OffsetDateTime;

public class PhoenixUpdater extends AbstractUpdater {

    // Metadata comes from the main assets endpoint
    private static final String METADATA_URL = "https://phoenixdevt.fr/api/nexus/assets";
    // Downloads use the authenticated endpoint
    private static final String DOWNLOAD_BASE = "https://phoenixdevt.fr/api/nexus/assets/";

    public PhoenixUpdater(SinceUpdater plugin) {
        super(plugin, "phoenix-updater.enabled", "phoenix-updater.check-interval", "[Phoenix-Updater]");
    }

    @Override
    protected void checkForUpdate() {
        String assetId = plugin.getConfigUtils().getString("phoenix-updater.user-asset-id");
        String token = plugin.getConfigUtils().getString("phoenix-updater.session-token");
        ConfigurationSection plugins = plugin.getConfigUtils().getConfig().getConfigurationSection("phoenix-updater.plugins");
        ConfigUtils dataUtils = plugin.getDataUtils();

        if (assetId == null || token == null || plugins == null) {
            plugin.getLogger().warning(logPrefix + " Update cancelled: config.yml is missing data.");
            return;
        }

        // 1. Fetch the JSON list of builds
        Request request = new Request.Builder()
                .url(METADATA_URL)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                plugin.getLogger().warning(logPrefix + " Failed to fetch build list (HTTP " + response.code() + ")");
                return;
            }

            String jsonString = response.body().string();
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");

            // 2. Loop through your plugins
            for (String pluginName : plugins.getKeys(false)) {
                String roleId = plugins.getString(pluginName);
                processBuild(pluginName, roleId, items, assetId, token, dataUtils);
            }

        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Update task failed: " + e.getMessage());
        }
    }

    private void processBuild(String name, String roleId, JsonArray items, String assetId, String token, ConfigUtils dataUtils) {
        String buildId = null;
        String version = "latest";
        OffsetDateTime latestDate = OffsetDateTime.MIN;

        // Scan the JSON items for the latest build of this plugin
        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            if (!item.has("maven2")) continue;
            JsonObject maven = item.getAsJsonObject("maven2");

            String artifactId = maven.get("artifactId").getAsString();

            // Match plugin name from config with artifactId in JSON
            if (artifactId.equalsIgnoreCase(name)) {
                OffsetDateTime itemDate = OffsetDateTime.parse(item.get("lastModified").getAsString());
                if (itemDate.isAfter(latestDate)) {
                    latestDate = itemDate;
                    buildId = item.get("id").getAsString();
                    version = maven.get("version").getAsString();
                }
            }
        }

        if (buildId == null) return;

        // Compare with history
        String historyKey = "phoenix_history." + name;
        if (buildId.equals(dataUtils.getString(historyKey))) return;

        plugin.getLogger().info(logPrefix + " New build for " + name + " found (Ver: " + version + ")");

        // Determine destination
        File currentFile = findJarInFolder(pluginsFolder, name);
        File outputFolder = (currentFile != null) ? updateFolder : pluginsFolder;
        String fileName = name + "-" + version + ".jar";

        // Final Download Link: https://phoenixdevt.fr/api/nexus/assets/{assetId}/{roleId}
        String downloadUrl = DOWNLOAD_BASE + assetId + "/" + roleId;

        if (downloadFileWithAuth(downloadUrl, outputFolder, fileName, token)) {
            plugin.getLogger().info(logPrefix + " Download complete: " + fileName + " saved to " + outputFolder.getName());
            dataUtils.setAndSave(historyKey, buildId);
        }
    }

    private boolean downloadFileWithAuth(String url, File folder, String fileName, String token) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return MythicUpdater.fileCheck(folder, fileName, response);
            } else {
                plugin.getLogger().severe(logPrefix + " Download failed (HTTP " + response.code() + ") for " + fileName);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}