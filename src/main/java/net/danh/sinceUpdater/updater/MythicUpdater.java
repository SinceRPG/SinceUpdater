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
        String globalCookie = plugin.getConfigUtils().getString("mythic-updater.cookie", "");
        ConfigUtils dataUtils = plugin.getDataUtils();

        for (String pluginName : downloads.getKeys(false)) {
            boolean isEnabled = downloads.getBoolean(pluginName + ".enabled", true);
            if (!isEnabled) continue;

            String pageUrl = downloads.getString(pluginName + ".page_url");
            String keyword = downloads.getString(pluginName + ".link_keyword", "build=");
            String cookie = downloads.getString(pluginName + ".cookie");

            if (cookie == null || cookie.trim().isEmpty()) {
                cookie = globalCookie;
            }

            if (pageUrl == null || keyword == null) continue;

            try {
                Request.Builder requestBuilder = new Request.Builder().url(pageUrl).header("User-Agent", userAgent);
                if (cookie != null && !cookie.isEmpty()) {
                    requestBuilder.header("Cookie", cookie);
                }

                String html;
                try (Response response = plugin.getHttpClient().newCall(requestBuilder.build()).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        plugin.getLogger().warning(logPrefix + " Could not access webpage for " + pluginName + " (HTTP " + response.code() + ")");
                        continue;
                    }
                    html = response.body().string();
                }

                String downloadLink = extractLinkFromHtml(html, keyword, pageUrl);
                if (downloadLink == null) {
                    if (cookie == null || cookie.trim().isEmpty()) {
                        plugin.getLogger().warning(logPrefix + " Could not find download link for " + pluginName + ". Cookie is not configured!");
                    } else {
                        plugin.getLogger().warning(logPrefix + " Could not find download link for " + pluginName + ". Cookie might be expired or account lacks access!");
                    }
                    continue;
                }

                String lastDownloaded = dataUtils.getString("last_downloaded." + pluginName);
                if (downloadLink.equals(lastDownloaded)) {
                    continue;
                }

                plugin.getLogger().info(logPrefix + " New version detected for " + pluginName + "!");

                String fileName = pluginName + ".jar";
                File currentRunning = findJarInFolder(pluginsFolder, pluginName);
                File outputFolder = (currentRunning != null) ? updateFolder : pluginsFolder;

                File pendingFile = findJarInFolder(outputFolder, pluginName);
                if (pendingFile != null) {
                    pendingFile.delete();
                }

                if (downloadFileWithHeaders(downloadLink, outputFolder, fileName, userAgent, cookie)) {
                    plugin.getLogger().info(logPrefix + " Successfully downloaded " + fileName + " to " + outputFolder.getName() + " folder.");
                    dataUtils.setAndSave("last_downloaded." + pluginName, downloadLink);
                }

            } catch (Exception e) {
                plugin.getLogger().severe(logPrefix + " Error processing " + pluginName + ": " + e.getMessage());
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

    private boolean downloadFileWithHeaders(String url, File destinationFolder, String fileName, String userAgent, String cookie) {
        Request.Builder requestBuilder = new Request.Builder().url(url).header("User-Agent", userAgent);
        if (cookie != null && !cookie.isEmpty()) {
            requestBuilder.header("Cookie", cookie);
        }

        try (Response response = plugin.getHttpClient().newCall(requestBuilder.build()).execute()) {
            return fileCheck(destinationFolder, fileName, response);
        } catch (Exception e) {
            plugin.getLogger().severe(logPrefix + " Download failed: " + e.getMessage());
            return false;
        }
    }
}