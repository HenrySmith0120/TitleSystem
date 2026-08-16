package com.henry.title.listener;

import com.henry.title.TitleSystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** 玩家进出/重生监听：加载数据、注册 Tab 显示、应用/移除 Buff 与粒子。 */
public final class PlayerListener implements Listener {

    private final TitleSystem plugin;

    public PlayerListener(TitleSystem plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 异步加载称号数据，完成后应用（Buff / 粒子）
        plugin.getTitleManager().loadPlayerAsync(player,
                () -> plugin.getTitleManager().applyActiveTitle(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getParticleManager().stop(player);
        plugin.getTitleManager().onQuit(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // 死亡会清空药水效果，重生后重新应用称号 Buff
        plugin.getTitleManager().applyActiveTitle(event.getPlayer());
    }
}
