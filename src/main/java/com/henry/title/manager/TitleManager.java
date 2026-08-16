package com.henry.title.manager;

import com.henry.title.TitleSystem;
import com.henry.title.data.DatabaseManager;
import com.henry.title.data.TitleEntry;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 称号核心管理器：玩家运行时缓存、给予/移除/穿戴/卸下、过期清理。
 * 数据库操作全部在异步线程执行，回主线程后再更新显示与 Buff（不阻塞主线程）。
 */
public final class TitleManager {

    private final TitleSystem plugin;
    private final Map<UUID, PlayerTitleData> cache = new ConcurrentHashMap<>();

    public TitleManager(TitleSystem plugin) {
        this.plugin = plugin;
    }

    /** 单个玩家的运行时数据。 */
    public static final class PlayerTitleData {
        public final Map<String, TitleEntry> owned = new ConcurrentHashMap<>();
        public volatile String activeTitleId;      // null = 未穿戴
    }

    public PlayerTitleData getData(UUID uuid) { return cache.get(uuid); }

    public boolean owns(UUID uuid, String titleId) {
        PlayerTitleData data = cache.get(uuid);
        return data != null && data.owned.containsKey(titleId);
    }

    public Collection<TitleEntry> getOwnedTitles(UUID uuid) {
        PlayerTitleData data = cache.get(uuid);
        return data == null ? Collections.emptyList() : data.owned.values();
    }

    public String getActiveTitleId(UUID uuid) {
        PlayerTitleData data = cache.get(uuid);
        return data == null ? null : data.activeTitleId;
    }

    public ConfiguredTitle getActiveTitle(UUID uuid) {
        String id = getActiveTitleId(uuid);
        return id == null ? null : plugin.getConfigManager().getTitle(id);
    }

    /** 异步加载玩家数据，完成后在玩家实体线程执行 callback（Folia 安全）。 */
    public void loadPlayerAsync(Player player, Runnable callback) {
        UUID uuid = player.getUniqueId();
        CompletableFuture.supplyAsync(() -> {
            try {
                DatabaseManager db = plugin.getDatabaseManager();
                List<TitleEntry> rows = db.loadPlayerTitles(uuid);
                PlayerTitleData data = new PlayerTitleData();
                String active = null;
                long now = System.currentTimeMillis();
                for (TitleEntry entry : rows) {
                    if (entry.isExpired(now)) {
                        // 加载时即发现过期：直接删库（自动清理的补充防线）
                        try {
                            db.deleteTitle(uuid, entry.getTitleId());
                        } catch (SQLException ignored) { }
                        continue;
                    }
                    data.owned.put(entry.getTitleId(), entry);
                    if (entry.isActive()) active = entry.getTitleId();
                }
                data.activeTitleId = active;
                cache.put(uuid, data);
                return data;
            } catch (SQLException ex) {
                plugin.getLogger().warning("加载玩家 " + uuid + " 称号数据失败: " + ex.getMessage());
                return null;
            }
        }).thenAccept(data -> runOnPlayer(player, callback));
    }

    /**
     * 给予称号（在线玩家）。expireTimeMillis = -1 表示永久。
     * done 在主线程执行，参数表示玩家此前是否已拥有该称号（用于“延长”提示）。
     */
    public void grant(Player target, String titleId, long expireTimeMillis, Consumer<Boolean> done) {
        UUID uuid = target.getUniqueId();
        PlayerTitleData data = cache.get(uuid);
        boolean existed = data != null && data.owned.containsKey(titleId);
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().saveTitle(uuid, titleId, System.currentTimeMillis(), expireTimeMillis);
            } catch (SQLException ex) {
                plugin.getLogger().warning("保存称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnPlayer(target, () -> {
            PlayerTitleData d = cache.get(uuid);
            if (d == null) {
                // 数据尚未加载：重新加载保证缓存一致
                loadPlayerAsync(target, () -> done.accept(existed));
            } else {
                TitleEntry prev = d.owned.get(titleId);
                boolean active = prev != null && prev.isActive();
                d.owned.put(titleId, new TitleEntry(uuid, titleId, System.currentTimeMillis(), expireTimeMillis, active));
                done.accept(existed);
            }
        }));
    }

    /** 穿戴称号（仅在线玩家，需已拥有）。 */
    public void equip(Player player, String titleId) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data == null) return;
        TitleEntry entry = data.owned.get(titleId);
        if (entry == null) return;
        if (entry.isExpired(System.currentTimeMillis())) {
            data.owned.remove(titleId);
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.getDatabaseManager().deleteTitle(player.getUniqueId(), titleId);
                } catch (SQLException ignored) { }
            });
            plugin.getMessageManager().send(player, "chest.expired", Map.of("title", titleId));
            return;
        }
        data.activeTitleId = titleId;
        // 异步持久化激活状态
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().setActiveTitle(player.getUniqueId(), titleId);
            } catch (SQLException ex) {
                plugin.getLogger().warning("保存激活称号失败: " + ex.getMessage());
            }
        });
        applyActiveTitle(player);
    }

    /** 卸下当前称号。 */
    public void unequip(Player player) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data == null) return;
        data.activeTitleId = null;
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().setActiveTitle(player.getUniqueId(), null);
            } catch (SQLException ex) {
                plugin.getLogger().warning("保存激活称号失败: " + ex.getMessage());
            }
        });
        applyActiveTitle(player);
    }

    /** 移除某称号（在线玩家）。done 在主线程执行。 */
    public void removeTitle(Player target, String titleId, Runnable done) {
        PlayerTitleData data = cache.get(target.getUniqueId());
        boolean wasActive = data != null && titleId.equals(data.activeTitleId);
        if (data != null) {
            data.owned.remove(titleId);
            if (wasActive) {
                data.activeTitleId = null;
                applyActiveTitle(target);
            }
        }
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().deleteTitle(target.getUniqueId(), titleId);
                if (wasActive) {
                    plugin.getDatabaseManager().setActiveTitle(target.getUniqueId(), null);
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("删除称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnPlayer(target, done));
    }

    /** 清空玩家所有称号（在线玩家，仅管理员命令触发）。done 在主线程执行。 */
    public void clearTitles(Player target, Runnable done) {
        PlayerTitleData data = cache.get(target.getUniqueId());
        if (data != null) {
            data.owned.clear();
            data.activeTitleId = null;
            applyActiveTitle(target);
        }
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getDatabaseManager().clearPlayerTitles(target.getUniqueId());
            } catch (SQLException ex) {
                plugin.getLogger().warning("清空称号失败: " + ex.getMessage());
            }
        }).thenRun(() -> runOnPlayer(target, done));
    }

    /** 依据当前激活称号刷新 Buff / 粒子（登录、换装、reload 后调用）。 */
    public void applyActiveTitle(Player player) {
        PlayerTitleData data = cache.get(player.getUniqueId());
        if (data == null) return;
        ConfiguredTitle title = data.activeTitleId == null ? null
                : plugin.getConfigManager().getTitle(data.activeTitleId);
        plugin.getBuffManager().apply(player, title);
        plugin.getParticleManager().updatePlayer(player, title);
    }

    /** 自动清理任务（在异步线程中执行）。 */
    public void cleanExpiredTitles() {
        try {
            List<String[]> removed = plugin.getDatabaseManager().cleanExpiredTitles(System.currentTimeMillis());
            if (removed.isEmpty()) return;
            // 本方法运行于异步线程：缓存为并发容器可直接修改；
            // 涉及玩家的操作（Buff/显示/消息）逐玩家投递到实体线程（Folia 安全）
            for (String[] row : removed) {
                UUID uuid = UUID.fromString(row[0]);
                Player player = Bukkit.getPlayer(uuid);
                if (player == null) continue;
                PlayerTitleData data = cache.get(uuid);
                if (data == null) continue;
                data.owned.remove(row[1]);
                if (row[1].equals(data.activeTitleId)) {
                    data.activeTitleId = null;
                    runOnPlayer(player, () -> {
                        applyActiveTitle(player);
                        plugin.getMessageManager().send(player, "chest.expired", Map.of("title", row[1]));
                    });
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("清理过期称号失败: " + ex.getMessage());
        }
    }

    /** 玩家退出：清理缓存。 */
    public void onQuit(UUID uuid) {
        cache.remove(uuid);
    }

    /** 调度到玩家实体线程（Folia 下操作玩家状态的安全方式；Paper 下即主线程）。 */
    private void runOnPlayer(Player player, Runnable task) {
        TaskUtils.runEntity(player, task);
    }
}
