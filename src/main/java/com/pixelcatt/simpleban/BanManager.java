package com.pixelcatt.simpleban;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;


public class BanManager {

    private final SimpleBan plugin;
    private final File dbFile;
    private Connection connection;

    private String announceBans = "Admins";
    private String timezone = "UTC";
    private String dateFormat = "dd/MM/yyyy HH:mm:ss";
    private boolean showTimeZone = true;

    public BanManager(SimpleBan plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "bans.db");
        openConnection();
        createTables();
    }

    private void openConnection() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().severe("Could not connect to SQLite database!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            // Bans Table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS bans (" +
                    "uuid TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "author TEXT NOT NULL," +
                    "reason TEXT NOT NULL," +
                    "unban_time BIGINT NOT NULL," +
                    "PRIMARY KEY(uuid))");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables in SQLite database.");
            e.printStackTrace();
        }
    }

    public void createBan(UUID uuid, String type, String author, String reason, String duration) {
        Player player = Bukkit.getPlayer(uuid);

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bans(uuid, type, author, reason, unban_time) VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO NOTHING")) {

            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.setString(3, author);
            ps.setString(4, reason);
            ps.setString(5, (duration.equals("-1") ? "-1" : plugin.convertDuration(duration)));

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sendBanMessage(UUID uuid, String name, CommandSender sender) {
        String[] info = getBanInfo(uuid);
        if (info == null || info.length < 4) {
            return;
        }

        String type = info[0];
        String author = info[1];
        String reason = info[2];
        String unban_time = getUnbanTime(info[3]);

        if (type == null) type = "ERROR";
        if (author == null) author = "ERROR";
        if (reason == null) reason = "ERROR";
        if (unban_time == null) unban_time = "ERROR";


        sender.sendMessage("§a" + name + " §chas been §a" + (type.equals("TEMP") ? "TEMPORARILY §cbanned for §a\"" + reason + "\" §cuntil §a" + unban_time : (type.equals("PERM") ? "PERMANENTLY §cbanned for §a\"" + reason + "\"" : type + " §cbanned")));


        switch (announceBans) {
            case "Admins":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(author) && (p.hasPermission("simpleban.admin") || p.hasPermission("simpleban.tempban") || p.hasPermission("simpleban.permban") || p.hasPermission("simpleban.banlist") || p.hasPermission("simpleban.baninfo") || p.hasPermission("simpleban.unban"))) {
                        p.sendMessage("§a" + name + " §chas been §a" + (type.equals("TEMP") ? "TEMPORARILY §cbanned by §a" + author + " §cfor §a\"" + reason + "\" §cuntil §a" + unban_time : (type.equals("PERM") ? "PERMANENTLY §cbanned by §a" + author + " §cfor §a\"" + reason + "\"" : type + " §cbanned by §a" + author)));
                    }
                }

                break;

            case "Players":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(author) && !(p.hasPermission("simpleban.admin") || p.hasPermission("simpleban.tempban") || p.hasPermission("simpleban.permban") || p.hasPermission("simpleban.banlist") || p.hasPermission("simpleban.baninfo") || p.hasPermission("simpleban.unban"))) {
                        p.sendMessage("§a" + name + " §chas been §a" + (type.equals("TEMP") ? "TEMPORARILY §cbanned by §a" + author + " §cfor §a\"" + reason + "\" §cuntil §a" + unban_time : (type.equals("PERM") ? "PERMANENTLY §cbanned by §a" + author + " §cfor §a\"" + reason + "\"" : type + " §cbanned by §a" + author)));
                    }
                }

                break;

            case "Everyone":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender)) {
                        p.sendMessage("§a" + name + " §chas been §a" + (type.equals("TEMP") ? "TEMPORARILY §cbanned by §a" + author + " §cfor §a\"" + reason + "\" §cuntil §a" + unban_time : (type.equals("PERM") ? "PERMANENTLY §cbanned by §a" + author + " §cfor §a\"" + reason + "\"" : type + " §cbanned by §a" + author)));
                    }
                }

                break;

            default:
                break;
        }
    }

    public boolean isBanned(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT unban_time FROM bans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.getLong("unban_time") <= Instant.now().getEpochSecond() && rs.getLong("unban_time") != -1) {
                    removeBan(uuid);
                    return false;
                } else {
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public String[] getBanInfo(UUID uuid) {
        String[] info = new String[4];
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT type, author, reason, unban_time FROM bans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info[0] = rs.getString("type");
                    info[1] = rs.getString("author");
                    info[2] = rs.getString("reason");
                    info[3] = rs.getString("unban_time");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return info;
    }

    public void removeBan(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bans WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeAllBans() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bans")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void sendUnBanMessage(String player, CommandSender sender) {
        String author = sender.getName();

        sender.sendMessage("§a" + player + " §chas been §aUNBANNED");

        switch (announceBans) {
            case "Admins":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && (p.hasPermission("simpleban.admin") || p.hasPermission("simpleban.tempban") || p.hasPermission("simpleban.permban") || p.hasPermission("simpleban.banlist") || p.hasPermission("simpleban.baninfo") || p.hasPermission("simpleban.unban"))) {
                        p.sendMessage("§a" + player + " §chas been §aUNBANNED §cby §a" + author);
                    }
                }

                break;

            case "Players":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender) && !(p.hasPermission("simpleban.admin") || p.hasPermission("simpleban.tempban") || p.hasPermission("simpleban.permban") || p.hasPermission("simpleban.banlist") || p.hasPermission("simpleban.baninfo") || p.hasPermission("simpleban.unban"))) {
                        p.sendMessage("§a" + player + " §chas been §aUNBANNED §cby §a" + author);
                    }
                }

                break;

            case "Everyone":
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(sender)) {
                        p.sendMessage("§a" + player + " §chas been §aUNBANNED §cby §a" + author);
                    }
                }

                break;

            default:
                break;
        }
    }

    public void removeExpiredBans() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bans WHERE unban_time <= ? AND type != 'PERM'")) {
            ps.setLong(1, Instant.now().getEpochSecond());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public TextComponent getBanMessage(UUID uuid) {
        if (uuid.toString().startsWith("00000000-0000-0000-0009")) {
            return getBedrockBanMessage(uuid);
        } else {
            return getJavaBanMessage(uuid);
        }
    }

    public TextComponent getJavaBanMessage(UUID uuid) {
        String[] info = getBanInfo(uuid);
        if (info == null || info.length < 4) {
            return new TextComponent(ChatColor.RED + "Failed to Load Ban-Data");
        }

        String type = info[0];
        String author = info[1];
        String reason = info[2];
        String expire_time = info[3];

        if (type == null) type = "ERROR";
        if (author == null) author = "ERROR";
        if (reason == null) reason = "ERROR";
        if (expire_time == null) expire_time = "ERROR";


        // ------------------------------------------------------------ \\


        TextComponent ban_message = new TextComponent();


        // 1. Ban Type

        TextComponent ban_type = new TextComponent();

        TextComponent l1p1 = new TextComponent("You are ");
        l1p1.setColor(ChatColor.RED);
        ban_type.addExtra(l1p1);

        TextComponent l1p2 = new TextComponent((type.equals("TEMP") ? "TEMPORARILY" : (type.equals("PERM") ? "PERMANENTLY" : type)));
        l1p2.setColor(ChatColor.AQUA);
        ban_type.addExtra(l1p2);

        TextComponent l1p3 = new TextComponent(" banned from this Server!");
        l1p3.setColor(ChatColor.RED);
        ban_type.addExtra(l1p3);

        ban_message.addExtra(ban_type);


        // 2. Seperator

        TextComponent seperator = new TextComponent();

        TextComponent l2p1 = new TextComponent("\n\n------------------------------------------------------------\n\n");
        l2p1.setColor(ChatColor.GRAY);
        seperator.addExtra(l2p1);

        ban_message.addExtra(seperator);


        // 3. Ban Author

        TextComponent ban_author = new TextComponent();

        TextComponent l3p1 = new TextComponent("Author: ");
        l3p1.setColor(ChatColor.GRAY);
        ban_author.addExtra(l3p1);

        TextComponent l3p2 = new TextComponent(author);
        l3p2.setColor(ChatColor.RED);
        ban_author.addExtra(l3p2);

        TextComponent l3p3 = new TextComponent("\n");
        l3p3.setColor(ChatColor.GRAY);
        ban_author.addExtra(l3p3);

        ban_message.addExtra(ban_author);


        // 4. Ban Reason

        TextComponent ban_reason = new TextComponent();

        TextComponent l4p1 = new TextComponent("Reason: ");
        l4p1.setColor(ChatColor.GRAY);
        ban_reason.addExtra(l4p1);

        TextComponent l4p2 = new TextComponent(reason);
        l4p2.setColor(ChatColor.RED);
        ban_reason.addExtra(l4p2);

        TextComponent l4p3 = new TextComponent("\n");
        l4p3.setColor(ChatColor.GRAY);
        ban_reason.addExtra(l4p3);

        ban_message.addExtra(ban_reason);


        // 5. Ban Expire-Time

        TextComponent ban_expire = new TextComponent();

        TextComponent l5p1 = new TextComponent("Expires: ");
        l5p1.setColor(ChatColor.GRAY);
        ban_expire.addExtra(l5p1);

        TextComponent l5p2 = new TextComponent((expire_time.equals("-1") ? "NEVER" : getUnbanTime(expire_time)));
        l5p2.setColor(ChatColor.RED);
        ban_expire.addExtra(l5p2);

        ban_message.addExtra(ban_expire);


        return ban_message;

    }

    public TextComponent getBedrockBanMessage(UUID uuid) {
        String[] info = getBanInfo(uuid);
        if (info == null || info.length < 4) {
            return new TextComponent(ChatColor.RED + "Failed to Load Ban-Data");
        }

        String type = info[0];
        String author = info[1];
        String reason = info[2];
        String expire_time = info[3];

        if (type == null) type = "ERROR";
        if (author == null) author = "ERROR";
        if (reason == null) reason = "ERROR";
        if (expire_time == null) expire_time = "ERROR";


        // ------------------------------------------------------------ \\


        TextComponent ban_message = new TextComponent();


        // 1. Ban Type

        TextComponent ban_type = new TextComponent();

        TextComponent p1 = new TextComponent("You have been ");
        p1.setColor(ChatColor.RED);
        ban_type.addExtra(p1);

        TextComponent p2 = new TextComponent((type.equals("TEMP") ? "TEMPORARILY" : (type.equals("PERM") ? "PERMANENTLY" : type)));
        p2.setColor(ChatColor.AQUA);
        ban_type.addExtra(p2);

        TextComponent p3 = new TextComponent(" banned from this Server");
        p3.setColor(ChatColor.RED);
        ban_type.addExtra(p3);

        ban_message.addExtra(ban_type);


        // 2. Seperator 1

        TextComponent seperator1 = new TextComponent();

        TextComponent p4 = new TextComponent(" by ");
        p4.setColor(ChatColor.GRAY);
        seperator1.addExtra(p4);

        ban_message.addExtra(seperator1);


        // 3. Ban Author

        TextComponent ban_author = new TextComponent();

        TextComponent p5 = new TextComponent(author);
        p5.setColor(ChatColor.RED);
        ban_author.addExtra(p5);

        ban_message.addExtra(ban_author);


        // 4. Seperator 2

        TextComponent seperator2 = new TextComponent();

        TextComponent p6 = new TextComponent(" for ");
        p6.setColor(ChatColor.GRAY);
        seperator2.addExtra(p6);

        ban_message.addExtra(seperator2);


        // 4. Ban Reason

        TextComponent ban_reason = new TextComponent();

        TextComponent p7 = new TextComponent("\"" + reason + "\"");
        p7.setColor(ChatColor.GRAY);
        ban_reason.addExtra(p7);

        ban_message.addExtra(ban_reason);

        // 5. TEMP-BANS
        if (type.equals("TEMP")) {
            // 5.1 Seperator 3

            TextComponent seperator3 = new TextComponent();

            TextComponent p8 = new TextComponent(" until ");
            p8.setColor(ChatColor.GRAY);
            seperator3.addExtra(p8);

            ban_message.addExtra(seperator3);


            // 5.2 Ban Expire-Time

            TextComponent ban_expire = new TextComponent();

            TextComponent p9 = new TextComponent((expire_time.equals("-1") ? "NEVER" : getUnbanTime(expire_time)));
            p9.setColor(ChatColor.RED);
            ban_expire.addExtra(p9);

            ban_message.addExtra(ban_expire);
        }


        return ban_message;

    }

    public String getUnbanTime(String unixTime) {
        try {
            long timestamp = Long.parseLong(unixTime);

            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.of(timezone));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);

            return dateTime.format(formatter) + (showTimeZone ? " " + (timezone.equals("UTC+0") || timezone.equals("UTC-0") ? "UTC" : timezone) : "");

        } catch (NumberFormatException e) {
            e.printStackTrace();

            return "ERROR";

        }
    }

    public void setAnnounceBans(String value) {
        this.announceBans = value;
    }

    public void setTimeZone(String value) {
        this.timezone = value;
    }

    public void setDateFormat(String value) {
        this.dateFormat = value;
    }

    public void setShowTimeZone(boolean value) {
        this.showTimeZone = value;
    }

}
