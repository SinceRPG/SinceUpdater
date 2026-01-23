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

    private static final String JENKINS_URL = "https://ci.athion.net/job/FastAsyncWorldEdit/lastSuccessfulBuild/api/json";

    public FaweUpdater(SinceUpdater plugin) {
        super(plugin, "fawe-updater.enabled", "fawe-updater.check-interval", "[FAWE-Updater]");
    }

    @Override
    protected void checkForUpdate() {
        Request request = new Request.Builder().url(JENKINS_URL).build();

        try (Response response = client.newCall(request).execute()) {
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

            // 1. Kiểm tra file đang chạy
            File currentRunning = findJarInFolder(pluginsFolder, "FastAsyncWorldEdit");

            // Nếu đang chạy và trùng tên -> Bỏ qua
            if (currentRunning != null && currentRunning.getName().equals(selectedFileName)) return;

            // 2. Xác định folder đích
            // - Có file cũ -> Tải vào 'update'
            // - Chưa có -> Tải vào 'plugins' (Cài mới)
            File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

            // 3. Kiểm tra xem file đích đã tồn tại chưa (tránh tải lại)
            File targetFile = findJarInFolder(outputFolder, "FastAsyncWorldEdit");
            if (targetFile != null) {
                if (targetFile.getName().equals(selectedFileName)) return; // Đã tải rồi
                targetFile.delete(); // Xóa bản chờ cũ
            }

            plugin.getLogger().info("[FAWE-Updater] Found version: " + selectedFileName);
            String url = "https://ci.athion.net/job/FastAsyncWorldEdit/lastSuccessfulBuild/artifact/" + selectedRelativePath;

            if (downloadFile(url, outputFolder, selectedFileName)) {
                String folderName = outputFolder.getName();
                plugin.getLogger().info("[FAWE-Updater] Downloaded to '" + folderName + "' folder.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}