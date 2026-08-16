package com.henry.title.hook;

import com.henry.title.TitleSystem;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济挂钩。
 * 不使用反射：显式插件检测 + Bukkit 服务管理器（ServicesManager）。
 *
 * 常见坑（务必了解）：
 *  1. Vault 只是 API 桥梁，本身不提供任何经济数据——服务器还必须安装至少一个
 *     经济插件（EssentialsX / CMI / EconomyBridge 等），由它向 Vault 注册 Economy 服务。
 *     只装 Vault 时检测结果是 PROVIDER_MISSING，而不是"未安装 Vault"。
 *  2. 经济插件可能晚于本插件启用（Vault 会在经济插件启用时注册其服务），
 *     因此 isReady() 在 economy 为空时会惰性重新探测，购买流程可自愈。
 */
public final class EconomyHook {

    /** 检测状态。 */
    public enum Status {
        /** 未安装 Vault 插件 */
        VAULT_MISSING,
        /** 已安装 Vault，但没有任何经济插件注册 Economy 服务 */
        PROVIDER_MISSING,
        /** 已就绪，可正常扣款 */
        READY
    }

    private Economy economy;
    private Status status = Status.VAULT_MISSING;

    public EconomyHook(TitleSystem plugin) {
        // 参数保留以保持主类调用一致性，本类无需持有插件引用
    }

    /** 检测并获取 Vault 经济实现（幂等，可重复调用）。 */
    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            status = Status.VAULT_MISSING;
            economy = null;
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            status = Status.PROVIDER_MISSING;
            economy = null;
            return false;
        }
        economy = rsp.getProvider();
        status = Status.READY;
        return true;
    }

    public Status getStatus() { return status; }

    /**
     * 是否可用。
     * 经济插件可能晚于本插件启用，因此 economy 为空时惰性重试探测。
     */
    public boolean isReady() {
        if (economy == null) setup();
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) { return economy.getBalance(player); }

    /** 扣款，成功返回 true。 */
    public boolean withdraw(Player player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public String format(double amount) { return economy.format(amount); }
}
