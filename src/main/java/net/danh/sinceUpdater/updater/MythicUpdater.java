package net.danh.sinceUpdater.updater;

import net.danh.sinceUpdater.SinceUpdater;
import net.danh.sinceUpdater.utils.ConfigUtils;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MythicUpdater extends AbstractUpdater {

    public MythicUpdater(SinceUpdater plugin) {
        // Kế thừa AbstractUpdater với thông số riêng cho Mythic
        super(plugin, "mythic-updater.enabled", "mythic-updater.check-interval", "[Mythic-Updater]");
    }

    public static boolean fileCheck(File destinationFolder, String fileName, Response response) throws IOException {
        if (!response.isSuccessful() || response.body() == null) return false;

        if (!destinationFolder.exists()) destinationFolder.mkdirs();

        File destFile = new File(destinationFolder, fileName);
        try (InputStream in = response.body().byteStream();
             FileOutputStream fos = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            return true;
        }
    }

    @Override
    protected void checkForUpdate() {
        ConfigurationSection downloads = plugin.getConfigUtils().getConfig().getConfigurationSection("mythic-updater.downloads");
        if (downloads == null) return;

        String userAgent = plugin.getConfigUtils().getString("mythic-updater.user_agent", "Mozilla/5.0");
        ConfigUtils dataUtils = plugin.getDataUtils();

        for (String pluginName : downloads.getKeys(false)) {
            String pageUrl = downloads.getString(pluginName + ".page_url");
            String keyword = downloads.getString(pluginName + ".link_keyword");
            String cookie = downloads.getString(pluginName + ".cookie");

            if (pageUrl == null || keyword == null) continue;

            try {
                // 1. Kéo mã nguồn HTML của trang web
                Request.Builder requestBuilder = new Request.Builder().url(pageUrl).header("User-Agent", userAgent);
                if (cookie != null && !cookie.isEmpty()) {
                    requestBuilder.header("Cookie", cookie);
                }

                String html;
                try (Response response = client.newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        plugin.getLogger().warning(logPrefix + " Không thể truy cập trang web cho " + pluginName);
                        continue;
                    }
                    html = response.body().string();
                }

                // 2. Tìm link tải bản build trên cùng
                String downloadLink = extractLinkFromHtml(html, keyword, pageUrl);
                if (downloadLink == null) {
                    plugin.getLogger().warning(logPrefix + " Không tìm thấy link tải " + pluginName + ". Cookie có thể đã hết hạn!");
                    continue;
                }

                // 3. Kiểm tra xem phiên bản này đã tải chưa
                String lastDownloaded = dataUtils.getString("last_downloaded." + pluginName);
                if (downloadLink.equals(lastDownloaded)) {
                    // Nếu trùng link -> Đã tải bản này rồi -> Bỏ qua cho nhẹ server
                    continue;
                }

                plugin.getLogger().info(logPrefix + " Phát hiện phiên bản mới cho " + pluginName + "!");

                // 4. Kiểm tra file đang chạy và xác định thư mục tải
                String fileName = pluginName + ".jar";
                File currentRunning = findJarInFolder(pluginsFolder, pluginName);
                File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

                // Xóa bản update cũ nếu có
                File pendingFile = findJarInFolder(outputFolder, pluginName);
                if (pendingFile != null) {
                    pendingFile.delete();
                }

                // 5. Tải file xuống
                if (downloadFileWithHeaders(downloadLink, outputFolder, fileName, userAgent, cookie)) {
                    plugin.getLogger().info(logPrefix + " Tải thành công " + fileName + " vào thư mục " + outputFolder.getName());
                    // Lưu lại link đã tải vào data.yml
                    dataUtils.setAndSave("last_downloaded." + pluginName, downloadLink);
                }

            } catch (Exception e) {
                plugin.getLogger().severe(logPrefix + " Lỗi khi xử lý " + pluginName + ": " + e.getMessage());
            }
        }
    }

    private String extractLinkFromHtml(String html, String keyword, String baseUrl) {
        String safeKeyword = Pattern.quote(keyword);
        Pattern pattern = Pattern.compile("href=\"([^\"]*" + safeKeyword + "[^\"]*)\"");
        Matcher matcher = pattern.matcher(html);

        if (matcher.find()) {
            String rawLink = matcher.group(1);
            rawLink = rawLink.replace("&amp;", "&");
            if (!rawLink.startsWith("http")) {
                rawLink = java.net.URI.create(baseUrl).resolve(rawLink).toString();
            }
            return rawLink;
        }
        return null;
    }

    /**
     * Viết lại hàm download riêng cho Mythic vì AbstractUpdater không hỗ trợ chèn Cookie Header
     */
    private boolean downloadFileWithHeaders(String url, File destinationFolder, String fileName, String userAgent, String cookie) {
        Request.Builder requestBuilder = new Request.Builder().url(url).header("User-Agent", userAgent);
        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {
            return fileCheck(destinationFolder, fileName, response);
        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Lỗi tải file: " + e.getMessage());
            return false;
        }
    }
}