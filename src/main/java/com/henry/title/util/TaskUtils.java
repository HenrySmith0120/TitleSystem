package com.henry.title.util;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * 任务调度工具类（Paper / Folia 现代调度器封装，Paper 1.21+）。
 *
 * <p>设计参考 DeluxeAuctions 的 TaskUtils，按本插件需要重写：</p>
 * <ul>
 *   <li>去除外部插件 InventoryAPI 硬依赖，改为可注入的 {@link OpenMenuProvider}；</li>
 *   <li>GUI 心跳任务改为"异步计时 + 实体线程检查/执行"，修复了原版在异步线程读取 GUI
 *       的竞态问题（Paper 下偶发错乱，Folia 下直接抛异常）；</li>
 *   <li>原版中 auctions/search 的硬编码特例属于拍卖插件业务，已移除；</li>
 *   <li>其余方法行为与原版一致（tick 单位、静态 API、构造器注入插件实例）。</li>
 * </ul>
 *
 * <p>使用前先 {@code new TaskUtils(plugin)} 注入插件实例；需要"菜单关闭自动取消"功能时，
 * 再 {@link #setOpenMenuProvider(OpenMenuProvider)} 注入菜单 ID 提供者。</p>
 *
 * <p>除明确标注外，所有延迟与周期参数均以 <b>tick</b> 为单位（1 tick = 50ms）。</p>
 */
public final class TaskUtils {

    /**
     * 玩家当前打开菜单的 ID 提供者（由菜单插件实现）。
     */
    @FunctionalInterface
    public interface OpenMenuProvider {
        /**
         * 返回玩家当前打开的菜单 ID，未打开任何菜单时返回 {@code null}。
         * 注意：该方法可能被异步线程调用，实现必须线程安全
         * （推荐底层使用 ConcurrentHashMap 保存"玩家 → 当前菜单 ID"映射）。
         *
         * @param player 玩家
         * @return 当前菜单 ID，未打开返回 null
         */
        String getOpenMenuId(Player player);
    }

    /**
     * 全局插件实例，由构造函数注入。
     */
    private static Plugin plugin;

    /**
     * 菜单 ID 提供者，可选；为 null 时 GUI 心跳任务在首个周期即自动取消（等同"未打开菜单"）。
     */
    private static OpenMenuProvider openMenuProvider;

    /**
     * 构造 TaskUtils 并保存全局插件实例。
     *
     * @param plugin 插件实例
     */
    public TaskUtils(Plugin plugin) {
        TaskUtils.plugin = plugin;
    }

    /**
     * 注入菜单 ID 提供者（使用 {@link #runTimerAsync(Player, String, Runnable, long, long)} 前调用）。
     *
     * @param provider 菜单 ID 提供者
     */
    public static void setOpenMenuProvider(OpenMenuProvider provider) {
        TaskUtils.openMenuProvider = provider;
    }

    /**
     * 获取构造函数传入的插件实例。
     *
     * @return 插件实例
     * @throws IllegalStateException 如果尚未通过构造函数初始化
     */
    private static Plugin getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("TaskUtils has not been initialized. Use new TaskUtils(Plugin) first.");
        }
        return plugin;
    }

    /**
     * 在全局区域调度器上执行任务。
     *
     * <p>在 Folia 上任务会运行在全局区域线程；在 Paper 上任务会运行在主线程。
     * 无论是哪种服务端，任务都会在下一个可用 tick 执行。</p>
     *
     * @param runnable 需要执行的任务
     */
    public static void run(Runnable runnable) {
        Plugin plugin = getPlugin();
        GlobalRegionScheduler scheduler = plugin.getServer().getGlobalRegionScheduler();
        scheduler.execute(plugin, runnable);
    }

    /**
     * 在异步调度器上尽快执行任务。
     *
     * @param runnable 需要执行的任务
     */
    public static void runAsync(Runnable runnable) {
        Plugin plugin = getPlugin();
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        scheduler.runNow(plugin, scheduledTask -> runnable.run());
    }

    /**
     * 在全局区域调度器上延迟执行任务。
     *
     * @param runnable   需要执行的任务
     * @param delayTicks 延迟时间，单位为 tick
     */
    public static void runLater(Runnable runnable, long delayTicks) {
        Plugin plugin = getPlugin();
        GlobalRegionScheduler scheduler = plugin.getServer().getGlobalRegionScheduler();
        scheduler.runDelayed(plugin, scheduledTask -> runnable.run(), delayTicks);
    }

    /**
     * 在异步调度器上延迟执行任务。
     *
     * @param runnable   需要执行的任务
     * @param delayTicks 延迟时间，单位为 tick
     */
    public static void runLaterAsync(Runnable runnable, long delayTicks) {
        Plugin plugin = getPlugin();
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        scheduler.runDelayed(plugin, scheduledTask -> runnable.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
    }

    /**
     * 在异步调度器上以固定频率循环执行任务。
     *
     * <p>任务在异步线程执行，内部不得触碰实体 / 方块 / 非线程安全 API。</p>
     *
     * @param runnable    需要执行的任务
     * @param delayTicks  初始延迟时间，单位为 tick
     * @param periodTicks 循环周期，单位为 tick
     */
    public static void runTimerAsync(Runnable runnable, long delayTicks, long periodTicks) {
        Plugin plugin = getPlugin();
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();
        scheduler.runAtFixedRate(plugin, scheduledTask -> runnable.run(),
                delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
    }

    /**
     * 在异步调度器上以固定频率循环执行任务，并在玩家当前菜单不匹配时自动取消。
     *
     * <p>每个周期：异步计时触发 → 通过 {@link EntityScheduler} 在玩家实体线程上
     * 检查当前菜单 ID（{@link OpenMenuProvider}）并执行任务。玩家未打开菜单、
     * 菜单 ID 与期望不一致、或玩家离线（实体退役）时，任务自动取消。</p>
     *
     * @param player      需要检查当前菜单的玩家
     * @param id          期望的菜单 id
     * @param runnable    需要执行的任务（在玩家实体线程执行，可安全操作 GUI）
     * @param delayTicks  初始延迟时间，单位为 tick
     * @param periodTicks 循环周期，单位为 tick
     */
    public static void runTimerAsync(Player player, String id, Runnable runnable, long delayTicks, long periodTicks) {
        Plugin plugin = getPlugin();
        AsyncScheduler scheduler = plugin.getServer().getAsyncScheduler();

        scheduler.runAtFixedRate(plugin, task -> {
            // 菜单检查与任务执行放到实体线程：修复原版在异步线程读取 GUI 的竞态，Folia 兼容
            player.getScheduler().run(plugin, scheduledTask -> {
                String inventoryId = openMenuProvider == null ? null : openMenuProvider.getOpenMenuId(player);
                if (inventoryId == null || !inventoryId.equalsIgnoreCase(id)) {
                    cancelTask(task);
                    return;
                }
                runnable.run();
            }, () -> cancelTask(task));
        }, delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
    }

    /**
     * 在实体调度器上执行任务。
     *
     * <p>任务会在实体所属区域的线程上于下一个 tick 执行。如果实体在任务执行前被移除
     * （例如死亡、被清除或所在区块被卸载），则任务不会执行。</p>
     *
     * @param entity   目标实体，任务将使用该实体的调度器
     * @param runnable 需要执行的任务
     */
    public static void runEntity(Entity entity, Runnable runnable) {
        Plugin plugin = getPlugin();
        EntityScheduler scheduler = entity.getScheduler();
        scheduler.run(plugin, scheduledTask -> runnable.run(), null);
    }

    /**
     * 在实体调度器上以固定频率循环执行任务（本类扩展方法，原版 TaskUtils 无此方法）。
     *
     * <p>任务在实体线程执行（Folia 下操作玩家状态的安全方式）。
     * 实体退役（玩家退出 / 实体死亡 / 所在区块卸载）后任务自动停止，无需手动取消。</p>
     *
     * @param entity      目标实体，任务将使用该实体的调度器
     * @param runnable    需要执行的任务
     * @param delayTicks  初始延迟，单位为 tick
     * @param periodTicks 循环周期，单位为 tick
     */
    public static void runEntityTimer(Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        Plugin plugin = getPlugin();
        // 实体调度器的 runAtFixedRate 要求初始延迟与周期均 >= 1 tick，
        // 传 0 会抛 IllegalArgumentException（Initial delay ticks may not be <= 0），这里做防御性钳制
        long initialDelay = Math.max(1, delayTicks);
        long period = Math.max(1, periodTicks);
        entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> runnable.run(), null, initialDelay, period);
    }

    /**
     * 在异步调度器上尽快执行任务。
     *
     * <p><b>警告</b>：异步任务与实体没有任何绑定关系，{@code entity} 参数当前未使用
     * （仅为与 {@link #runEntity(Entity, Runnable)} 保持 API 对称而保留）。
     * 任务内严禁触碰实体 / 方块 / GUI 等非线程安全对象。</p>
     *
     * @param entity   实体参数，当前未使用
     * @param runnable 需要执行的任务
     */
    public static void runEntityAsync(Entity entity, Runnable runnable) {
        runAsync(runnable);
    }

    /**
     * 取消指定的调度任务。
     *
     * @param task 需要取消的任务，允许为 null
     */
    private static void cancelTask(ScheduledTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
