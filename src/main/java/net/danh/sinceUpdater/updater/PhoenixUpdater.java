package net.danh.sinceUpdater.updater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.utils.ConfigUtils;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;

public class PhoenixUpdater extends AbstractUpdater {

    private static final String API_URL = "https://phoenixdevt.fr/api/nexus/devbuilds";

    public PhoenixUpdater(SinceUpdater plugin) {
        super(plugin, "phoenix-updater.enabled", "phoenix-updater.check-interval", "[Phoenix-Updater]");
    }

    @Override
    protected void checkForUpdate() {
        String token = plugin.getConfigUtils().getString("phoenix-updater.session-token");
        List<String> targetPlugins = plugin.getConfigUtils().getConfig().getStringList("phoenix-updater.plugins");
        ConfigUtils dataUtils = plugin.getDataUtils(); // Sử dụng data.yml để lưu lịch sử

        if (token == null || token.isEmpty() || token.equals("YOUR_SESSION_TOKEN_HERE")) {
            plugin.getLogger().warning(logPrefix + " Please provide a valid session-token in config.yml!");
            return;
        }

        Request request = new Request.Builder()
                .url(API_URL)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                plugin.getLogger().warning(logPrefix + " Failed to connect to API. Token might be expired.");
                return;
            }

            String jsonString = response.body().string();
            JsonObject root = JsonParser.parseString(jsonString).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");

            for (String artifactId : targetPlugins) {
                processPhoenixPlugin(artifactId, items, token, dataUtils);
            }

        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Error during update check: " + e.getMessage());
        }
    }

    private void processPhoenixPlugin(String artifactId, JsonArray items, String token, ConfigUtils dataUtils) {
        String latestUrl = null;
        OffsetDateTime latestDate = OffsetDateTime.MIN;
        String latestVersion = null;
        String buildId = null;

        // 1. Tìm bản build mới nhất trong danh sách JSON
        for (JsonElement el : items) {
            JsonObject item = el.getAsJsonObject();
            JsonObject maven2 = item.getAsJsonObject("maven2");

            if (maven2.get("artifactId").getAsString().equalsIgnoreCase(artifactId)) {
                OffsetDateTime itemDate = OffsetDateTime.parse(item.get("lastModified").getAsString());
                if (itemDate.isAfter(latestDate)) {
                    latestDate = itemDate;
                    latestUrl = item.get("downloadUrl").getAsString();
                    latestVersion = maven2.get("version").getAsString();
                    buildId = item.get("id").getAsString(); // Lấy ID duy nhất của build
                }
            }
        }

        if (latestUrl == null) return;

        // 2. Kiểm tra lịch sử trong data.yml (giống Mythic)
        // Chúng ta so sánh buildId hoặc timestamp để biết có phải bản mới không
        String lastDownloadedId = dataUtils.getString("phoenix_history." + artifactId);
        if (buildId.equals(lastDownloadedId)) {
            // Đã tải bản này rồi, không làm gì cả
            return;
        }

        plugin.getLogger().info(logPrefix + " New build found for " + artifactId + " (Build Date: " + latestDate + ")");

        // 3. Xác định tên file và folder đích
        String fileName = artifactId + "-" + latestVersion + ".jar";
        File currentFile = findJarInFolder(pluginsFolder, artifactId);
        File outputFolder = (currentFile != null) ? updateFolder : pluginsFolder;

        // Xóa các file cũ đang chờ trong folder update
        File pendingFile = findJarInFolder(outputFolder, artifactId);
        if (pendingFile != null) {
            pendingFile.delete();
        }

        // 4. Tải file
        if (downloadWithAuth(latestUrl, outputFolder, fileName, token)) {
            plugin.getLogger().info(logPrefix + " Successfully downloaded " + fileName + " to " + outputFolder.getName());

            // 5. Lưu lại Build ID vào data.yml để lần sau không tải lại bản này nữa
            dataUtils.setAndSave("phoenix_history." + artifactId, buildId);
        }
    }

    private boolean downloadWithAuth(String url, File destFolder, String fileName, String token) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Cookie", "__Secure-next-auth.session-token=" + token)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return MythicUpdater.fileCheck(destFolder, fileName, response);
        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Download failed for " + fileName + ": " + e.getMessage());
            return false;
        }
    }
}