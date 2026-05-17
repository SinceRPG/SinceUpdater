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

    private final String apiMetadata;
    private final String downloadUrlRoot;

    public PhoenixUpdater(SinceUpdater plugin) {
        super(plugin, "phoenix-updater.enabled", "phoenix-updater.check-interval", "[Phoenix-Updater]");
        this.apiMetadata = plugin.getConfigUtils().getString("urls.phoenix.api-metadata", "https://phoenixdevt.fr/api/nexus/assets");
        this.downloadUrlRoot = plugin.getConfigUtils().getString("urls.phoenix.download-root", "https://phoenixdevt.fr/api/nexus/assets/");
    }

    @Override
    protected void checkForUpdate() {
        String token = plugin.getConfigUtils().getString("phoenix-updater.session-token");
        ConfigurationSection plugins = plugin.getConfigUtils().getConfig().getConfigurationSection("phoenix-updater.plugins");
        ConfigUtils dataUtils = plugin.getDataUtils();

        if (token == null || token.isEmpty()) {
            plugin.getLogger().warning(logPrefix + " Setup incomplete: 'session-token' is missing in config.yml.");
            return;
        }

        Request request = new Request.Builder()
                .url(apiMetadata)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .header("Accept", "application/json")
                .build();

        try (Response response = plugin.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                plugin.getLogger().warning(logPrefix + " Connection failed (HTTP " + response.code() + "). Token might be expired.");
                return;
            }

            String jsonString = response.body().string();
            if (jsonString.trim().startsWith("<")) {
                plugin.getLogger().severe(logPrefix + " Received HTML instead of JSON. Authentication failed.");
                return;
            }

            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");

            for (String pluginName : plugins.getKeys(false)) {
                String roleId = plugins.getString(pluginName);
                processPlugin(pluginName, roleId, items, token, dataUtils);
            }

        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Task failed: " + e.getMessage());
        }
    }

    private void processPlugin(String name, String roleId, JsonArray items, String token, ConfigUtils dataUtils) {
        String specificSha512 = null;
        String buildId = null;
        String version = "latest";
        OffsetDateTime latestDate = OffsetDateTime.MIN;

        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            if (!item.has("maven2") || !item.has("checksum")) continue;

            JsonObject maven = item.getAsJsonObject("maven2");
            String artifactId = maven.get("artifactId").getAsString();

            if (artifactId.equalsIgnoreCase(name)) {
                OffsetDateTime itemDate = OffsetDateTime.parse(item.get("lastModified").getAsString());
                if (itemDate.isAfter(latestDate)) {
                    latestDate = itemDate;
                    buildId = item.get("id").getAsString();
                    version = maven.get("version").getAsString();
                    specificSha512 = item.getAsJsonObject("checksum").get("sha512").getAsString();
                }
            }
        }

        if (specificSha512 == null || buildId == null) return;

        String historyKey = "phoenix_history." + name;
        if (buildId.equals(dataUtils.getString(historyKey))) return;

        plugin.getLogger().info(logPrefix + " New version for " + name + " detected: " + version);

        String downloadUrl = downloadUrlRoot + specificSha512 + "/" + roleId;

        File currentFile = findJarInFolder(pluginsFolder, name);
        File outputFolder = (currentFile != null) ? updateFolder : pluginsFolder;
        String fileName = name + "-" + version + ".jar";

        if (downloadFileWithAuth(downloadUrl, outputFolder, fileName, token)) {
            plugin.getLogger().info(logPrefix + " Successfully downloaded " + fileName + " (" + outputFolder.getName() + " folder)");
            dataUtils.setAndSave(historyKey, buildId);
        }
    }

    private boolean downloadFileWithAuth(String url, File folder, String fileName, String token) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .build();

        try (Response response = plugin.getHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                return MythicUpdater.fileCheck(folder, fileName, response);
            } else {
                plugin.getLogger().severe(logPrefix + " Download blocked for " + fileName + " (HTTP " + response.code() + ")");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Error: " + e.getMessage());
            return false;
        }
    }
}