package com.henry.title;

import com.henry.title.command.TitleCommand;
import com.henry.title.config.ConfigManager;
import com.henry.title.config.MessageManager;
import com.henry.title.data.DatabaseManager;
import com.henry.title.hook.EconomyHook;
import com.henry.title.hook.PlaceholderHook;
import com.henry.title.listener.GuiListener;
import com.henry.title.listener.PlayerListener;
import com.henry.title.manager.BuffManager;
import com.henry.title.manager.ParticleManager;
import com.henry.title.manager.TitleManager;
import com.henry.title.util.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TitleSystem 称号系统主类（Paper 1.21.8+ / Paper API 1.21.8）。
 *
 * 安全承诺（全部可审计）：
 *  1. 不发起任何 HTTP / 外部网络请求；
 *  2. 不使用反射、URLClassLoader、Runtime.exec / ProcessBuilder；
 *  3. 所有命令均在 plugin.yml 声明，权限节点齐全；不触碰 NMS；
 *  4. 所有数据库 DML 一律 PreparedStatement；无 DROP TABLE / TRUNCATE 等危险语句；
 *  5. 配置与语言文件全部为本地文件，不下载任何远程资源。
 */
public final class TitleSystem extends JavaPlugin {

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private TitleManager titleManager;
    private BuffManager buffManager;
    private ParticleManager particleManager;
    private EconomyHook economyHook;
    private PlaceholderHook placeholderHook;

    @Override
    public void onEnable() {
        // 0. 必需依赖检查：称号展示完全依赖 PlaceholderAPI 变量，缺失则拒绝启动
        // （plugin.yml 已声明 depend: [PlaceholderAPI]，此处为兜底防御）
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("缺少必需依赖 PlaceholderAPI，插件无法启动。请先安装 PlaceholderAPI。");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 1. 配置与语言（本地文件）
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);
        this.messageManager.reload();

        // 初始化静态调度器工具类（注入插件实例）
        new TaskUtils(this);

        // 2. 数据库（HikariCP + SQLite/MySQL，见 config.yml）
        this.databaseManager = new DatabaseManager(this, configManager.getStorageConfig());
        try {
            this.databaseManager.init();
        } catch (Exception e) {
            getLogger().severe("数据库初始化失败，插件无法启动: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 3. 可选挂钩（均为显式插件检测，不使用任何反射）
        this.economyHook = new EconomyHook(this);
        if (this.economyHook.setup()) {
            getLogger().info("检测到 Vault 与经济服务，商店金币购买功能已启用");
        } else if (this.economyHook.getStatus() == EconomyHook.Status.VAULT_MISSING) {
            getLogger().info("未检测到 Vault 插件，付费称号购买将被禁用（免费称号不受影响）");
        } else {
            getLogger().warning("已检测到 Vault，但尚未发现任何经济插件（EssentialsX/CMI 等）注册经济服务；"
                    + "付费称号购买暂不可用，购买时会自动重试检测");
        }
        // 经济插件可能晚于本插件启用：20 tick 后再探测一次（Vault 会在经济插件启用时注册其服务）
        TaskUtils.runLater(() -> {
            if (!economyHook.isReady() && economyHook.setup()) {
                getLogger().info("延迟检测到经济服务，商店金币购买功能已启用");
            }
        }, 20L);
        this.placeholderHook = new PlaceholderHook(this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderHook.register();
            getLogger().info("检测到 PlaceholderAPI，已注册 %titlesystem_*% 占位符");
        }
        if (Bukkit.getPluginManager().getPlugin("SuperTrails") != null
                || Bukkit.getPluginManager().getPlugin("PlayerParticles") != null) {
            getLogger().info("检测到粒子插件(SuperTrails/PlayerParticles)：本插件使用内置原版粒子引擎（详见 README 扩展说明）");
        }

        // 4. 管理器
        this.titleManager = new TitleManager(this);
        this.buffManager = new BuffManager(this);
        this.particleManager = new ParticleManager(this);

        // 5. 监听器
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new GuiListener(this), this);

        // 6. 命令（仅 plugin.yml 声明的 /titles）
        TitleCommand command = new TitleCommand(this);
        var cmd = getCommand("titles");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }

        // 7. 自动清理过期称号（异步定时任务，Paper 异步调度器）
        if (configManager.isAutoCleanEnabled()) {
            long seconds = Math.max(60L, configManager.getAutoCleanIntervalSeconds());
            TaskUtils.runTimerAsync(() -> titleManager.cleanExpiredTitles(), 200L, seconds * 20L);
        }

        // 8. 服务器 reload（/reload confirm）时已在线玩家：补加载数据并应用称号
        TaskUtils.runLater(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                titleManager.loadPlayerAsync(p, () -> titleManager.applyActiveTitle(p));
            }
        }, 20L);

        getLogger().info("TitleSystem v" + getPluginMeta().getVersion() + " 已启用（开源 / 无混淆 / 可审计）");
    }

    @Override
    public void onDisable() {
        // 全局/异步任务由 Paper 在插件卸载时自动取消，实体任务随实体退役自动清理，无需手动取消
        if (particleManager != null) particleManager.shutdown();
        if (placeholderHook != null) {
            try {
                placeholderHook.unregister();
            } catch (Exception ignored) {
                // 忽略卸载阶段的异常
            }
        }
        if (databaseManager != null) databaseManager.close();
        getLogger().info("TitleSystem 已卸载");
    }

    // ---- getters ----
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public TitleManager getTitleManager() { return titleManager; }
    public BuffManager getBuffManager() { return buffManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public EconomyHook getEconomyHook() { return economyHook; }
}
