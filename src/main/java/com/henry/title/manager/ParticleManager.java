package com.henry.title.manager;

import com.henry.title.TitleSystem;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.model.ParticleConfig;
import com.henry.title.util.TaskUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 粒子特效管理器：佩戴带粒子的称号时，按周期在玩家身边刷原版粒子。
 * 使用 org.bukkit.Particle 枚举（覆盖 1.21 全部粒子），需要数据参数的粒子（DUST 等）
 * 通过 World#spawnParticle 的泛型重载传入 DustOptions。
 *
 * 调度模型（Folia 兼容）：每个玩家会话内仅启动一个实体调度器任务
 * （{@link TaskUtils#runEntityTimer}），每 tick 从 active 配置表读取当前粒子并刷出。
 * 换称号/卸称号只需更新配置表；玩家退出后任务随实体退役自动结束，无需手动取消。
 * 注意：修改 particle-task-interval 后，已在线玩家需重新进服（或插件重启）才生效。
 *
 * SuperTrails / PlayerParticles 等第三方粒子插件如需深度联动，可基于 Hook 机制扩展
 * （见 README），本插件默认使用内置原版粒子引擎作为回退。
 */
public final class ParticleManager {

    private final TitleSystem plugin;
    /** 玩家当前生效的粒子配置（null = 无粒子）。 */
    private final Map<UUID, ParticleConfig> active = new ConcurrentHashMap<>();
    /** 已为该玩家会话启动实体调度器任务的标记。 */
    private final Set<UUID> started = ConcurrentHashMap.newKeySet();

    public ParticleManager(TitleSystem plugin) {
        this.plugin = plugin;
    }

    /**
     * 依据玩家当前称号更新粒子配置，并在首次佩戴时启动实体调度器任务。
     * 注意：ConcurrentHashMap 不允许 null 值，因此"无粒子"用移除键表示（任务空转，读取时判空即可）。
     */
    public void updatePlayer(Player player, ConfiguredTitle title) {
        ParticleConfig pc = (title == null) ? null : title.getParticle();
        UUID uuid = player.getUniqueId();
        if (pc == null) {
            // 无粒子：移除配置（任务保持空转，免取消设计）
            active.remove(uuid);
        } else {
            active.put(uuid, pc);
        }
        if (pc != null && started.add(uuid)) {
            // 初始延迟必须 >= 1 tick（实体调度器限制），用 1 即可立即生效
            TaskUtils.runEntityTimer(player, () -> {
                ParticleConfig cfg = active.get(uuid);
                if (cfg != null && player.isOnline()) {
                    spawn(player, cfg);
                }
            }, 1L, plugin.getConfigManager().getParticleTaskInterval());
        }
    }

    private void spawn(Player player, ParticleConfig pc) {
        Location loc = player.getLocation().add(0, 2.1, 0);
        if (pc.dust() != null) {
            // 需要数据参数的粒子（DUST 等）走泛型重载
            player.getWorld().spawnParticle(pc.type(), loc, pc.count(),
                    pc.offset(), pc.offset(), pc.offset(), 0, pc.dust());
        } else {
            player.getWorld().spawnParticle(pc.type(), loc, pc.count(),
                    pc.offset(), pc.offset(), pc.offset(), pc.extra());
        }
    }

    /** 玩家退出：清理标记。实体调度器任务随实体退役自动结束，无需手动取消。 */
    public void stop(Player player) {
        active.remove(player.getUniqueId());
        started.remove(player.getUniqueId());
    }

    public void shutdown() {
        active.clear();
        started.clear();
    }
}
