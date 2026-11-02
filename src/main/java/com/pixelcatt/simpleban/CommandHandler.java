package com.pixelcatt.simpleban;

import com.pixelcatt.offlineplayerlib.CachedOfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;


public class CommandHandler implements CommandExecutor {

    private final SimpleBan plugin;
    private final BanManager manager;
    private boolean isConsole = false;

    public CommandHandler(SimpleBan plugin, BanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof ConsoleCommandSender || sender instanceof Player) {
            if (sender instanceof ConsoleCommandSender) {
                isConsole = true;
            }
        } else {
            return false;
        }

        String cmd = command.getName().toLowerCase();

        switch (cmd) {
            case "tempban":
                if (sender.hasPermission("simpleban.tempban") || isConsole) {
                    if (args.length < 2) {
                        return false;
                    }

                    plugin.getOfflinePlayerLib().GetOfflinePlayerByName(args[0]).thenAccept(target -> {
                        if (target == null) {
                            sender.sendMessage("§cPlayer not found: §a" + args[0]);
                        } else if (manager.isBanned(target.getUniqueId())) {
                            sender.sendMessage("§cPlayer is already banned: §a" + args[0]);
                        } else {
                            plugin.rememberOfflinePlayer(target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
                            handleTempBan(sender, args, target.getUniqueId());
                        }
                    });
                    return true;

                } else {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

            case "permban":
                if (sender.hasPermission("simpleban.permban") || isConsole) {
                    if (args.length < 1) {
                        return false;
                    }

                    plugin.getOfflinePlayerLib().GetOfflinePlayerByName(args[0]).thenAccept(target -> {
                        if (target == null) {
                            sender.sendMessage("§cPlayer not found: §a" + args[0]);
                        } else if (manager.isBanned(target.getUniqueId())) {
                            sender.sendMessage("§cPlayer is already banned: §a" + args[0]);
                        } else {
                            plugin.rememberOfflinePlayer(target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
                            handlePermBan(sender, args, target.getUniqueId());
                        }
                    });
                    return true;

                } else {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

            case "banlist":
                if (sender.hasPermission("simpleban.banlist") || isConsole) {
                    if (args.length != 0) {
                        return false;
                    }
                    return handleBanList(sender);

                } else {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

            case "baninfo":
                if (sender.hasPermission("simpleban.baninfo") || isConsole) {
                    if (args.length != 1) {
                        return false;
                    }
                    return handleBanInfo(sender, args[0]);

                } else {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

            case "unban":
                if (sender.hasPermission("simpleban.unban") || isConsole) {
                    if (args.length != 1) {
                        return false;
                    }
                    return handleUnBan(sender, args[0]);

                } else {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }

            default:
                return false;
        }
    }

    private void handleTempBan(CommandSender sender, String[] args, UUID targetUUID) {
        String targetName = args[0];
        String duration = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "None";

        if (!duration.matches("^(?!.*s.*s)(?!.*m.*m)(?!.*h.*h)(?!.*d.*d)(?!.*w.*w)(?!.*y.*y)(\\d{1,4}[smhdwy])+$")) {
            sender.sendMessage("§cInvalid Duration. Use this Format: §a'10s' | '2h30m' | '1d12h' | '27w3d45m' | '2w1y3d25h9999s5m'");
            return;
        }

        manager.createBan(targetUUID, "TEMP", sender.getName(), reason, duration);

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null) targetPlayer.kickPlayer(manager.getBanMessage(targetPlayer.getUniqueId()).toLegacyText());

        manager.sendBanMessage(targetUUID, targetName, sender);

    }

    private void handlePermBan(CommandSender sender, String[] args, UUID targetUUID) {
        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "None";

        manager.createBan(targetUUID, "PERM", sender.getName(), reason, "-1");

        Player targetPlayer = Bukkit.getPlayer(targetUUID);
        if (targetPlayer != null) targetPlayer.kickPlayer(manager.getBanMessage(targetPlayer.getUniqueId()).toLegacyText());

        manager.sendBanMessage(targetUUID, targetName, sender);

    }

    private boolean handleBanList(CommandSender sender) {
        plugin.getOfflinePlayerLib().GetAllOfflinePlayers().thenAccept(players -> {
            List<String> bannedPlayers = new ArrayList<>();
            for (CachedOfflinePlayer player : players) {
                String name = player.getName();
                if (name != null && manager.isBanned(player.getUniqueId())) {
                    bannedPlayers.add(name);
                }
            }
            sender.sendMessage("§aBanned Players:\n" + "§c" + (bannedPlayers.isEmpty() ? "None" : String.join("\n", bannedPlayers)));
        });
        return true;
    }

    private boolean handleBanInfo(CommandSender sender, String targetName) {
        plugin.getOfflinePlayerLib().GetOfflinePlayerByName(targetName).thenAccept(player -> {
            if (player == null || !manager.isBanned(player.getUniqueId())) {
                sender.sendMessage("§cPlayer is not banned: §a" + targetName);
                return;
            }
            plugin.rememberOfflinePlayer(player.getUniqueId(), player.getName() == null ? targetName : player.getName());

            String[] info = manager.getBanInfo(player.getUniqueId());

            String type = info[0];
            String author = info[1];
            String reason = info[2];
            String expire_time = info[3];

            if (type == null) type = "ERROR";
            if (author == null) author = "ERROR";
            if (reason == null) reason = "ERROR";
            if (expire_time == null) expire_time = "ERROR";

            sender.sendMessage("§aBan-Info for " + targetName + ":\n" +
                    "§9Type: " + "§c" + (type.equals("PERM") ? "PERMANENT" : (type.equals("TEMP") ? "TEMPORARY" : type)) + "\n" +
                    "§9Author: " + "§c" + author + "\n" +
                    "§9Reason: " + "§c" + reason + "\n" +
                    "§9Expires: " + "§c" + (expire_time.equals("-1") ? "NEVER" : manager.getUnbanTime(expire_time)));
        });
        return true;
    }

    private boolean handleUnBan(CommandSender sender, String targetName) {
        if (targetName.equals("*")) {
            manager.removeAllBans();
            manager.sendUnBanMessage("Every Player", sender);

            return true;
        }

        plugin.getOfflinePlayerLib().GetOfflinePlayerByName(targetName).thenAccept(player -> {
            if (player == null) {
                sender.sendMessage("§cPlayer not found: §a" + targetName);
                return;
            }
            if (!manager.isBanned(player.getUniqueId())) {
                sender.sendMessage("§cPlayer is not banned: §a" + targetName);
                return;
            }

            plugin.rememberOfflinePlayer(player.getUniqueId(), player.getName() == null ? targetName : player.getName());
            manager.removeBan(player.getUniqueId());
            manager.sendUnBanMessage(targetName, sender);
        });

        return true;
    }
}
