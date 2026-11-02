package com.pixelcatt.simpleban;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {

    private final SimpleBan plugin;
    private final BanManager manager;

    public TabCompleter(SimpleBan plugin, BanManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (sender instanceof Player) {
            plugin.refreshOfflinePlayerNames();
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();

            plugin.rememberOfflinePlayer(uuid, player.getName());

            String cmd = command.getName().toLowerCase();

            switch (cmd) {
                case "tempban":
                    return autocompleteTempBan(args);
                case "permban":
                    return autocompleteUnBannedPlayers(args);
                case "unban", "baninfo":
                    return autocompleteBannedPlayers(args);

                default:
                    return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> autocompleteTempBan(String[] args) {
        if (args.length == 1) {
            return autocompleteUnBannedPlayers(args);
        } else if (args.length == 2) {
            return "smhdwy".chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .filter(c -> !args[1].contains(c))
                    .toList();
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> autocompleteUnBannedPlayers(String[] args) {
        if (args.length == 1) {
            List<String> names = plugin.getOfflinePlayerNames().entrySet().stream()
                .filter(entry -> !manager.isBanned(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
            return filterStringsByPrefix(names, args[0]);
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> autocompleteBannedPlayers(String[] args) {
        if (args.length == 1) {
            List<String> names = plugin.getOfflinePlayerNames().entrySet().stream()
                .filter(entry -> manager.isBanned(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
            return filterStringsByPrefix(names, args[0]);
        } else {
            return Collections.emptyList();
        }
    }

    private List<String> filterStringsByPrefix(List<String> stringList, String prefix) {
        if (prefix == null || prefix.isEmpty() || stringList == null || stringList.isEmpty()) {
            return stringList;
        } else {
            return stringList.stream()
                    .filter(string -> string.toLowerCase().startsWith(prefix.toLowerCase()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
        }
    }
}
