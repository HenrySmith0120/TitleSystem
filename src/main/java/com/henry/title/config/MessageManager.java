package com.henry.title.config;

import com.henry.title.TitleSystem;
import com.henry.title.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 多语言消息管理：messages_zh.yml / messages_en.yml（均为本地文件，不下载任何远程资源）。
 * 首次加载时从 jar 复制默认文件，并把 jar 内新增的键合并到用户文件中（升级安全）。
 */
public final class MessageManager {

    private final TitleSystem plugin;
    private FileConfiguration messages;

    public MessageManager(TitleSystem plugin) {
        this.plugin = plugin;
    }

    /** 依据 config.yml 的 language 加载语言文件。 */
    public void reload() {
        String lang = plugin.getConfigManager().getLanguage();
        String fileName = lang.equalsIgnoreCase("en_US") ? "messages_en.yml" : "messages_zh.yml";
        File folder = new File(plugin.getDataFolder(), "messages");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("无法创建语言目录: " + folder.getAbsolutePath());
        }
        File file = new File(folder, fileName);
        if (!file.exists()) {
            plugin.saveResource("messages/" + fileName, false);
        }
        try (InputStream in = plugin.getResource("messages/" + fileName)) {
            if (in != null) {
                FileConfiguration jarCfg = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                YamlConfiguration fileCfg = YamlConfiguration.loadConfiguration(file);
                boolean changed = false;
                for (String key : jarCfg.getKeys(true)) {
                    if (!fileCfg.contains(key)) {
                        fileCfg.set(key, jarCfg.get(key));
                        changed = true;
                    }
                }
                // 清理历史版本废弃键：
                //  - display.true/display.false：未加引号的 on:/off: 被 YAML 1.1 解析为布尔键的历史坏键；
                //  - display.on/display.off 与 chest.display-*：显示开关功能已移除
                for (String obsolete : new String[]{
                        "display.true", "display.false", "display.on", "display.off",
                        "chest.display-status-on", "chest.display-status-off", "chest.display-toggle-hint"}) {
                    if (fileCfg.contains(obsolete)) {
                        fileCfg.set(obsolete, null);
                        changed = true;
                    }
                }
                if (changed) {
                    try {
                        fileCfg.save(file);
                    } catch (IOException e) {
                        plugin.getLogger().warning("保存语言文件失败: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("读取语言文件失败: " + e.getMessage());
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    /** 原始消息文本（不含占位符替换）。 */
    public String getRaw(String key) {
        Object o = messages.get(key);
        return o == null ? key : String.valueOf(o);
    }

    /** 发送消息：占位符替换 + & 颜色代码 + （玩家）PlaceholderAPI 解析，全程 Adventure 组件。 */
    public void send(CommandSender target, String key, Map<String, String> placeholders) {
        String raw = getRaw(key).replace("%prefix%", getRaw("prefix"));
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                String value = e.getValue() == null ? "" : e.getValue();
                raw = raw.replace("%" + e.getKey() + "%", value);
            }
        }
        if (target instanceof Player player) {
            player.sendMessage(TextUtil.parse(raw, player, Map.of()));
        } else {
            target.sendMessage(TextUtil.parse(raw, null, Map.of()));
        }
    }

    public void send(CommandSender target, String key) {
        send(target, key, null);
    }

    /** 天数 → 文本（-1/0 = 永久）。 */
    public String duration(int days) {
        return days <= 0 ? getRaw("time.permanent") : days + getRaw("time.day");
    }

    /** 剩余时间 → 人性化文本（永久 / 已过期 / x天x小时x分）。 */
    public String timeLeft(long expireTimeMillis) {
        if (expireTimeMillis < 0) return getRaw("time.permanent");
        long remain = expireTimeMillis - System.currentTimeMillis();
        if (remain <= 0) return getRaw("time.expired");
        long days = remain / 86400000L;
        remain %= 86400000L;
        long hours = remain / 3600000L;
        remain %= 3600000L;
        long minutes = remain / 60000L;
        remain %= 60000L;
        long seconds = remain / 1000L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(getRaw("time.day"));
        if (hours > 0) sb.append(hours).append(getRaw("time.hour"));
        if (minutes > 0) sb.append(minutes).append(getRaw("time.minute"));
        if (sb.length() == 0) sb.append(seconds).append(getRaw("time.second"));
        return sb.toString();
    }
}
