package com.henry.title.command;

import com.henry.title.TitleSystem;
import com.henry.title.data.TitleEntry;
import com.henry.title.gui.ChestGui;
import com.henry.title.gui.ShopGui;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * /title 命令执行器。
 * 命令本体与权限节点均在 plugin.yml 声明（无隐藏命令），此处只做分发与参数校验。
 * 子命令：
 *   玩家:   shop / chest
 *   管理员: give <玩家> <称号ID> [天数] / remove <玩家> <称号ID> / clear <玩家> / list [页] / reload
 */
public final class TitleCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE_SIZE = 10;

    private final TitleSystem plugin;

    public TitleCommand(TitleSystem plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "shop" -> cmdShop(sender);
            case "chest" -> cmdChest(sender);
            case "give" -> cmdGive(sender, args);
            case "remove" -> cmdRemove(sender, args);
            case "clear" -> cmdClear(sender, args);
            case "list" -> cmdList(sender, args);
            case "reload" -> cmdReload(sender);
            case "help" -> sendUsage(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    // ---------- 玩家命令 ----------

    private void cmdShop(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !hasPerm(sender, "title.user")) return;
        if (plugin.getTitleManager().getData(player.getUniqueId()) == null) {
            plugin.getMessageManager().send(player, "data-loading");
            return;
        }
        ShopGui.open(plugin, player, 0);
    }

    private void cmdChest(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !hasPerm(sender, "title.user")) return;
        if (plugin.getTitleManager().getData(player.getUniqueId()) == null) {
            plugin.getMessageManager().send(player, "data-loading");
            return;
        }
        ChestGui.open(plugin, player, 0);
    }

    // ---------- 管理员命令 ----------

    private void cmdGive(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "title.admin")) return;
        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "usage", Map.of("usage", "/title give <玩家> <称号ID> [天数]"));
            return;
        }
        String playerName = args[1];
        String titleId = args[2];
        ConfiguredTitle title = plugin.getConfigManager().getTitle(titleId);
        if (title == null) {
            plugin.getMessageManager().send(sender, "title-not-found", Map.of("title", titleId));
            return;
        }
        int days = -1; // 缺省 = 永久
        if (args.length >= 4) {
            try {
                days = Integer.parseInt(args[3]);
                if (days <= 0) days = -1;
            } catch (NumberFormatException e) {
                plugin.getMessageManager().send(sender, "invalid-number", Map.of("number", args[3]));
                return;
            }
        }
        long expire = days > 0 ? System.currentTimeMillis() + days * 86400000L : -1L;
        String durationText = plugin.getMessageManager().duration(days);

        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            plugin.getTitleManager().grant(target, titleId, expire, existed ->
                    plugin.getMessageManager().send(sender, existed ? "give.extended" : "give.success",
                            Map.of("player", target.getName(), "title", title.getDisplay(), "duration", durationText)));
            return;
        }
        // 离线玩家：直接写库
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.getName() == null) {
            plugin.getMessageManager().send(sender, "player-not-found", Map.of("player", playerName));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().saveTitle(offline.getUniqueId(), titleId,
                        System.currentTimeMillis(), expire);
            } catch (SQLException ex) {
                plugin.getLogger().warning("保存称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnSender(sender, () ->
                plugin.getMessageManager().send(sender, "give.success",
                        Map.of("player", offline.getName(), "title", title.getDisplay(), "duration", durationText))));
    }

    private void cmdRemove(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "title.admin")) return;
        if (args.length < 3) {
            plugin.getMessageManager().send(sender, "usage", Map.of("usage", "/title remove <玩家> <称号ID>"));
            return;
        }
        String playerName = args[1];
        String titleId = args[2];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            if (!plugin.getTitleManager().owns(target.getUniqueId(), titleId)) {
                plugin.getMessageManager().send(sender, "remove.not-owned",
                        Map.of("player", target.getName(), "title", titleId));
                return;
            }
            plugin.getTitleManager().removeTitle(target, titleId, () ->
                    plugin.getMessageManager().send(sender, "remove.success",
                            Map.of("player", target.getName(), "title", titleId)));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.getName() == null) {
            plugin.getMessageManager().send(sender, "player-not-found", Map.of("player", playerName));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().deleteTitle(offline.getUniqueId(), titleId);
            } catch (SQLException ex) {
                plugin.getLogger().warning("删除称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnSender(sender, () ->
                plugin.getMessageManager().send(sender, "remove.success",
                        Map.of("player", offline.getName(), "title", titleId))));
    }

    private void cmdClear(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "title.admin")) return;
        if (args.length < 2) {
            plugin.getMessageManager().send(sender, "usage", Map.of("usage", "/title clear <玩家>"));
            return;
        }
        String playerName = args[1];
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            plugin.getTitleManager().clearTitles(target, () ->
                    plugin.getMessageManager().send(sender, "clear.success", Map.of("player", target.getName())));
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.getName() == null) {
            plugin.getMessageManager().send(sender, "player-not-found", Map.of("player", playerName));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().clearPlayerTitles(offline.getUniqueId());
            } catch (SQLException ex) {
                plugin.getLogger().warning("清空称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnSender(sender, () ->
                plugin.getMessageManager().send(sender, "clear.success", Map.of("player", offline.getName()))));
    }

    private void cmdList(CommandSender sender, String[] args) {
        if (!hasPerm(sender, "title.admin")) return;
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                plugin.getMessageManager().send(sender, "invalid-number", Map.of("number", args[1]));
                return;
            }
        }
        List<ConfiguredTitle> all = new ArrayList<>(plugin.getConfigManager().getTitles().values());
        if (all.isEmpty()) {
            plugin.getMessageManager().send(sender, "command.list-empty");
            return;
        }
        int pages = Math.max(1, (int) Math.ceil(all.size() / (double) LIST_PAGE_SIZE));
        if (page < 1) page = 1;
        if (page > pages) page = pages;
        plugin.getMessageManager().send(sender, "command.list-header",
                Map.of("page", String.valueOf(page), "pages", String.valueOf(pages)));
        boolean vault = plugin.getEconomyHook().isReady();
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(all.size(), start + LIST_PAGE_SIZE);
        for (int i = start; i < end; i++) {
            ConfiguredTitle t = all.get(i);
            String price = vault ? plugin.getEconomyHook().format(t.getPrice()) : formatPrice(t.getPrice());
            plugin.getMessageManager().send(sender, "command.list-item", Map.of(
                    "title", t.getDisplay(),
                    "price", price,
                    "duration", plugin.getMessageManager().duration(t.getDurationDays())));
        }
    }

    private void cmdReload(CommandSender sender) {
        if (!hasPerm(sender, "title.admin")) return;
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        // 在线玩家重新加载并应用（Buff / 显示 / 粒子随新配置刷新）
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getTitleManager().loadPlayerAsync(p,
                    () -> plugin.getTitleManager().applyActiveTitle(p));
        }
        plugin.getMessageManager().send(sender, "command.reload-success");
    }

    // ---------- 辅助 ----------

    private void sendUsage(CommandSender sender) {
        String usage = sender.hasPermission("title.admin")
                ? "/title shop|chest|give <玩家> <ID> [天数]|remove <玩家> <ID>|clear <玩家>|list [页]|reload"
                : "/title shop|chest";
        plugin.getMessageManager().send(sender, "usage", Map.of("usage", usage));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.getMessageManager().send(sender, "player-only");
        return null;
    }

    private boolean hasPerm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.getMessageManager().send(sender, "no-permission");
        return false;
    }

    /** 调度到发送者线程：玩家走实体调度器（Folia 安全），控制台走全局调度器。 */
    private void runOnSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            TaskUtils.runEntity(player, task);
        } else {
            TaskUtils.run(task);
        }
    }

    private static String formatPrice(double price) {
        if (price == Math.floor(price) && !Double.isInfinite(price)) {
            return String.valueOf((long) price);
        }
        return String.valueOf(price);
    }

    // ---------- Tab 补全 ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("shop", "chest", "help"));
            if (sender.hasPermission("title.admin")) {
                subs.addAll(List.of("give", "remove", "clear", "list", "reload"));
            }
            return filter(subs, args[0]);
        }
        if (args.length == 2 && sender.hasPermission("title.admin")) {
            switch (args[0].toLowerCase()) {
                case "give", "remove", "clear" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                    return filter(names, args[1]);
                }
                default -> { return List.of(); }
            }
        }
        if (args.length == 3 && sender.hasPermission("title.admin")) {
            if (args[0].equalsIgnoreCase("give")) {
                return filter(new ArrayList<>(plugin.getConfigManager().getTitles().keySet()), args[2]);
            }
            if (args[0].equalsIgnoreCase("remove")) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target != null) {
                    List<String> ids = new ArrayList<>();
                    for (TitleEntry e : plugin.getTitleManager().getOwnedTitles(target.getUniqueId())) {
                        ids.add(e.getTitleId());
                    }
                    return filter(ids, args[2]);
                }
            }
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission("title.admin")) {
            return List.of("7", "30", "365", "-1");
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        if (prefix == null || prefix.isEmpty()) return options;
        List<String> out = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) out.add(o);
        }
        return out;
    }
}
