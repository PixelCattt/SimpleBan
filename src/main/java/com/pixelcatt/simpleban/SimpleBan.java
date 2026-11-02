package com.pixelcatt.simpleban;

import com.pixelcatt.offlineplayerlib.CachedOfflinePlayer;
import com.pixelcatt.offlineplayerlib.OfflinePlayerLib;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SimpleBan extends JavaPlugin {

    private BanManager manager;
    private LoginListener loginListener;
    private JoinListener joinListener;
    private OfflinePlayerLib offlinePlayerLib;
    private final Map<UUID, String> offlinePlayerNames = new ConcurrentHashMap<>();
    private volatile long offlinePlayerNamesLastRefresh;


    @Override
    public void onEnable() {
        getLogger().info("~ Created by PixelCatt ~");

        offlinePlayerLib = (OfflinePlayerLib) getServer().getPluginManager().getPlugin("OfflinePlayerLib");
        refreshOfflinePlayerNames();

        // Initialize Ban-Manager
        getLogger().info("Initializing Ban-Manager...");
        manager = new BanManager(this);

        // Initialize Event Listeners
        getLogger().info("Initializing Event Listeners...");
        initializeEventListeners();
        
        // Load Configuration
        getLogger().info("Loading Configuration...");
        loadConfig();

        // Register Commands
        getLogger().info("Registering Commands...");
        registerCommands();

        // Remove Expired Bans
        getLogger().info("Removing Expired Bans...");
        manager.removeExpiredBans();

        // Check for Updates
        getLogger().info("Checking for Updates...");
        checkForUpdates();
    }


    public void initializeEventListeners() {
        loginListener = new LoginListener(this, manager);
        getServer().getPluginManager().registerEvents(loginListener, this);
        joinListener = new JoinListener(this, manager);
        getServer().getPluginManager().registerEvents(joinListener, this);
    }

    public OfflinePlayerLib getOfflinePlayerLib() {
        return offlinePlayerLib;
    }

    public void rememberOfflinePlayer(UUID uuid, String name) {
        if (uuid != null && name != null) offlinePlayerNames.put(uuid, name);
    }

    public Map<UUID, String> getOfflinePlayerNames() {
        return Map.copyOf(offlinePlayerNames);
    }

    public void refreshOfflinePlayerNames() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - offlinePlayerNamesLastRefresh < 5000) return;
        offlinePlayerNamesLastRefresh = currentTime;
        offlinePlayerLib.GetAllOfflinePlayers().thenAccept(players -> {
            for (CachedOfflinePlayer player : players) {
                rememberOfflinePlayer(player.getUniqueId(), player.getName());
            }
        });
    }

    private void loadConfig() {
        String announceBans = getConfig().getString("announce-bans", "Admins");
        String timeZone = getConfig().getString("timezone", "UTC");
        String dateFormat = getConfig().getString("date-format", "dd/MM/yyyy HH:mm:ss");
        boolean showTimeZone = getConfig().getBoolean("show-timezone", true);
        String configVersion = getConfig().getString("config-version", "1.0.0");
        String currentVersion = getDescription().getVersion();

        saveResource("config.yml", true);
        reloadConfig();
        FileConfiguration config = getConfig();

        if (!("Admins".equals(announceBans) || "Players".equals(announceBans) || "Everyone".equals(announceBans) || "No".equals(announceBans))) {
            getLogger().warning("Configuration Error: \"announce-bans\" was configured incorrectly and reset to \"Admins\".");
            announceBans = "Admins";
        }
        manager.setAnnounceBans(announceBans);
        config.set("announce-bans", announceBans);

        try { ZoneId.of(timeZone); } catch (DateTimeException e) {
            getLogger().warning("Configuration Error: \"timezone\" was configured incorrectly and reset to \"UTC\".");
            timeZone = "UTC";
        }
        manager.setTimeZone(timeZone);
        config.set("timezone", timeZone);

        try { LocalDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern(dateFormat)); } catch (DateTimeException e) {
            getLogger().warning("Configuration Error: \"date-format\" was configured incorrectly and reset to \"dd/MM/yyyy HH:mm:ss\".");
            dateFormat = "dd/MM/yyyy HH:mm:ss";
        }
        manager.setDateFormat(dateFormat);
        config.set("date-format", dateFormat);

        manager.setShowTimeZone(showTimeZone);
        config.set("show-timezone", showTimeZone);

        if (isNewerVersion(configVersion, "1.0.0")) {
            if (isOlderVersion(configVersion, currentVersion)) {
                configVersion = currentVersion;
            }
        } else {
            configVersion = currentVersion;
        }
        config.set("config-version", configVersion);

        saveConfig();
    }

    private void registerCommands() {
        CommandHandler commandHandler = new CommandHandler(this, manager);
        TabCompleter tabCompleter = new TabCompleter(this, manager);

        getCommand("tempban").setExecutor(commandHandler);
        getCommand("permban").setExecutor(commandHandler);
        getCommand("banlist").setExecutor(commandHandler);
        getCommand("baninfo").setExecutor(commandHandler);
        getCommand("unban").setExecutor(commandHandler);

        getCommand("tempban").setTabCompleter(tabCompleter);
        getCommand("permban").setTabCompleter(tabCompleter);
        getCommand("banlist").setTabCompleter(tabCompleter);
        getCommand("baninfo").setTabCompleter(tabCompleter);
        getCommand("unban").setTabCompleter(tabCompleter);
    }

    private void checkForUpdates() {
        String[] latestVersion = getLatestVersion().split("\\|", 2);
        String currentVersion = getDescription().getVersion();

        if (!"error".equals(latestVersion[0])) {
            if (isNewerVersion(latestVersion[0], currentVersion)) {
                getLogger().warning("A new Version of SimpleBan is available: " + latestVersion[0]);
                joinListener.setUpdateAvailable(true);
            } else {
                getLogger().info("No new Updates available.");
            }
        } else {
            getLogger().warning("Failed to Check for Updates!\n" + latestVersion[1]);
        }
    }

    public String getLatestVersion() {
        String apiUrl = "https://api.modrinth.com/v2/project/simple_ban/version";

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Accept", "application/json")
                    .build();

            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JSONArray jsonArray = new JSONArray(response.body());
                    if (!jsonArray.isEmpty()) {
                        JSONObject latestVersion = jsonArray.getJSONObject(0);
                        return latestVersion.getString("version_number");
                    } else {
                        return "error|No Version Data Found: Project has no Versions on Modrinth";
                    }
                } else {
                    return "error|No Version Data Found: Failed to Connect to Modrinth API";
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("Failed to check for Updates!");

                StringWriter stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));
                return "error|" + stackTrace;
            }
        }
    }

    public boolean isNewerVersion(String comparingVersion, String referenceVersion) {
        String[] comparingVersionParts = comparingVersion.split("\\.");
        String[] referenceVersionParts = referenceVersion.split("\\.");

        for (int i = 0; i < 3; i++) {
            int comparingVersionPart = i < comparingVersionParts.length ? Integer.parseInt(comparingVersionParts[i]) : 0;
            int referenceVersionPart = i < referenceVersionParts.length ? Integer.parseInt(referenceVersionParts[i]) : 0;

            if (comparingVersionPart > referenceVersionPart) {
                return true;
            } else if (comparingVersionPart < referenceVersionPart) {
                return false;
            }
        }

        return false;
    }

    public boolean isOlderVersion(String comparingVersion, String referenceVersion) {
        String[] comparingVersionParts = comparingVersion.split("\\.");
        String[] referenceVersionParts = referenceVersion.split("\\.");

        for (int i = 0; i < 3; i++) {
            int comparingVersionPart = i < comparingVersionParts.length ? Integer.parseInt(comparingVersionParts[i]) : 0;
            int referenceVersionPart = i < referenceVersionParts.length ? Integer.parseInt(referenceVersionParts[i]) : 0;

            if (comparingVersionPart < referenceVersionPart) {
                return true;
            } else if (comparingVersionPart > referenceVersionPart) {
                return false;
            }
        }

        return false;
    }


    // ------------------------------------------------------------ \\


    public String convertDuration(String input) {
        long totalSeconds = 0;

        input = input.toLowerCase();

        Pattern pattern = Pattern.compile("(\\d{1,4})([smhdwy])");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            char unit = matcher.group(2).charAt(0);

            switch (unit) {
                case 's': totalSeconds += value; break;
                case 'm': totalSeconds += value * 60L; break;
                case 'h': totalSeconds += value * 3600L; break;
                case 'd': totalSeconds += value * 86400L; break;
                case 'w': totalSeconds += value * 604800L; break;
                case 'y': totalSeconds += value * 31536000L; break;
            }
        }

        return String.valueOf(Instant.now().getEpochSecond() + totalSeconds);
    }
}
