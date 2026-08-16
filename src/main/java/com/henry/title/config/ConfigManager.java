package com.henry.title.config;

import com.henry.title.TitleSystem;
import com.henry.title.model.AttributeSpec;
import com.henry.title.model.ConfiguredTitle;
import com.henry.title.model.GuiDecoration;
import com.henry.title.model.GuiLayout;
import com.henry.title.model.GuiStyle;
import com.henry.title.model.ParticleConfig;
import com.henry.title.model.TitleBuff;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理器：分文件管理配置——
 *  config.yml（主配置：语言/存储/自动清理/粒子周期）、
 *  gui.yml（GUI 标题/布局/样式/装饰）、
 *  titles.yml（称号定义，根键为称号 ID，顺序即商店展示顺序）。
 * 药水效果使用 1.21 注册表（Registry.EFFECT）解析，兼容 1.21 新增效果；
 * 属性使用 1.21.2+ 现代名称（Attribute.MAX_HEALTH / ATTACK_DAMAGE ...）。
 */
public final class ConfigManager {

    private final TitleSystem plugin;
    private FileConfiguration config;        // config.yml
    private FileConfiguration guiConfig;    // gui.yml
    private FileConfiguration titlesConfig; // titles.yml
    private final Map<String, ConfiguredTitle> titles = new LinkedHashMap<>();

    public ConfigManager(TitleSystem plugin) {
        this.plugin = plugin;
        reload();
    }

    /** 重新加载全部配置并解析称号定义。 */
    public void reload() {
        plugin.reloadConfig();
        // 确保独立配置文件存在（不存在时从 jar 解压默认文件）
        plugin.saveResource("gui.yml", false);
        plugin.saveResource("titles.yml", false);
        File guiFile = new File(plugin.getDataFolder(), "gui.yml");
        File titlesFile = new File(plugin.getDataFolder(), "titles.yml");
        FileConfiguration guiLoaded = YamlConfiguration.loadConfiguration(guiFile);
        FileConfiguration titlesLoaded = YamlConfiguration.loadConfiguration(titlesFile);

        upgradeConfigs(guiFile, titlesFile, guiLoaded, titlesLoaded);

        this.config = plugin.getConfig();
        this.guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        this.titlesConfig = YamlConfiguration.loadConfiguration(titlesFile);

        this.titles.clear();
        for (String id : this.titlesConfig.getKeys(false)) {
            ConfiguredTitle title = parseTitle(id, this.titlesConfig.getConfigurationSection(id));
            if (title != null) {
                this.titles.put(id, title);
            }
        }
        plugin.getLogger().info("已加载 " + this.titles.size() + " 个称号定义（titles.yml）");
    }

    /**
     * 配置自动升级（幂等，随每次 reload 执行）：
     *  1. v4 一次性迁移：旧 config.yml 中的 gui / titles 段拆分为独立的 gui.yml / titles.yml
     *     （旧值原样迁移，缺失键用 jar 默认补齐）；
     *  2. 三个文件分别清理废弃键并合并 jar 默认键（不覆盖用户已有值）。
     * 用户自定义内容（语言/存储/称号定义等）原样保留。
     */
    private void upgradeConfigs(File guiFile, File titlesFile,
                                FileConfiguration gui, FileConfiguration titles) {
        FileConfiguration current = plugin.getConfig();
        int version = current.getInt("config-version", 1);

        if (version < 4) {
            // v4 迁移：gui / titles 段拆分为独立文件
            ConfigurationSection oldGui = current.getConfigurationSection("gui");
            ConfigurationSection oldTitles = current.getConfigurationSection("titles");
            if (oldGui != null) {
                for (String key : oldGui.getKeys(true)) {
                    gui.set(key, oldGui.get(key));
                }
                if (version < 3) {
                    // 未经历 v3 迁移的旧布局：强制应用 v3 默认槽位
                    gui.set("shop.prev-slot", 48);
                    gui.set("shop.next-slot", 50);
                    gui.set("shop.close-slot", 53);
                    gui.set("shop.player-head-slot", 45);
                    gui.set("chest.prev-slot", 48);
                    gui.set("chest.next-slot", 50);
                    gui.set("chest.close-slot", 53);
                    gui.set("chest.player-head-slot", 45);
                }
                current.set("gui", null);
            }
            if (oldTitles != null) {
                for (String key : oldTitles.getKeys(true)) {
                    titles.set(key, oldTitles.get(key));
                }
                current.set("titles", null);
            }
        }

        upgradeFile(current, loadJar("config.yml"),
                new String[]{"bstats", "display", "gui", "titles"},
                new File(plugin.getDataFolder(), "config.yml"), "config.yml");
        upgradeFile(gui, loadJar("gui.yml"),
                new String[]{"chest.display-toggle-slot"}, guiFile, "gui.yml");
        upgradeFile(titles, loadJar("titles.yml"), new String[]{}, titlesFile, "titles.yml");

        if (version < 4) {
            current.set("config-version", 4);
            plugin.saveConfig();
            plugin.getLogger().info("配置已拆分为 config.yml / gui.yml / titles.yml（旧内容已迁移）");
        }
    }

    /** 单文件升级：清理废弃键 + 合并 jar 默认键（不覆盖已有值），有变更时保存。 */
    private void upgradeFile(FileConfiguration current, FileConfiguration jarDefaults,
                             String[] obsoleteKeys, File file, String fileName) {
        boolean changed = false;
        List<String> removed = new ArrayList<>();
        for (String key : obsoleteKeys) {
            if (current.contains(key)) {
                current.set(key, null);
                removed.add(key);
                changed = true;
            }
        }
        for (String key : jarDefaults.getKeys(true)) {
            if (!current.contains(key)) {
                current.set(key, jarDefaults.get(key));
                changed = true;
            }
        }
        if (changed) {
            try {
                current.save(file);
                if (!removed.isEmpty()) {
                    plugin.getLogger().info(fileName + "：已移除废弃键 " + String.join(", ", removed));
                }
            } catch (IOException e) {
                plugin.getLogger().warning("保存 " + fileName + " 失败: " + e.getMessage());
            }
        }
    }

    /** 从 jar 读取默认配置文件。 */
    private FileConfiguration loadJar(String name) {
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) {
                return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("读取默认配置 " + name + " 失败: " + e.getMessage());
        }
        return new YamlConfiguration();
    }

    // ---------- 称号解析 ----------

    private ConfiguredTitle parseTitle(String id, ConfigurationSection s) {
        if (s == null) return null;
        String display = s.getString("display", id);
        String name = s.getString("name", display);
        List<String> lore = s.getStringList("lore");
        Material material = Material.matchMaterial(s.getString("material", ""));
        if (material == null) {
            plugin.getLogger().warning("称号 " + id + " 的 material 无效，已跳过该称号");
            return null;
        }
        int modelData = s.getInt("model-data", 0);
        double price = Math.max(0.0, s.getDouble("price", 0.0));
        String permission = s.getString("permission"); // 可空：不填则购买无需权限
        int durationDays = s.getInt("duration-days", -1); // -1 = 永久
        TitleBuff buffs = parseBuffs(id, s.getConfigurationSection("buffs"));
        ParticleConfig particle = parseParticle(id, s.getConfigurationSection("particle"));
        return new ConfiguredTitle(id, display, name, lore, material, modelData, price,
                permission, durationDays, buffs, particle);
    }

    private TitleBuff parseBuffs(String id, ConfigurationSection s) {
        List<PotionEffect> potions = new ArrayList<>();
        List<AttributeSpec> attributes = new ArrayList<>();
        if (s == null) return new TitleBuff(potions, attributes);

        // 药水效果（佩戴期间无限时长，卸下时移除）
        for (Object o : s.getList("potions", Collections.emptyList())) {
            if (!(o instanceof Map<?, ?>)) continue;
            Map<String, Object> m = stringMap((Map<?, ?>) o);
            String effectName = String.valueOf(m.get("effect"));
            if (effectName == null || effectName.equals("null")) continue;
            // 1.21+ 从注册表解析药水效果，支持 SPEED/JUMP_BOOST/INFESTED/OOZING/WEAVING 等
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effectName.toLowerCase()));
            if (type == null) {
                plugin.getLogger().warning("称号 " + id + " 的药水效果 " + effectName + " 无效，已跳过");
                continue;
            }
            int amplifier = asInt(m.get("amplifier"), 0); // 0 = 等级 I
            boolean ambient = asBool(m.get("ambient"), false);
            boolean showParticles = asBool(m.get("show-particles"), false);
            boolean showIcon = asBool(m.get("show-icon"), true);
            potions.add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, ambient, showParticles, showIcon));
        }

        // 属性加成（AttributeModifier：现代 Operation 枚举 + EquipmentSlotGroup）
        for (Object o : s.getList("attributes", Collections.emptyList())) {
            if (!(o instanceof Map<?, ?>)) continue;
            Map<String, Object> m = stringMap((Map<?, ?>) o);
            String attrName = String.valueOf(m.get("attribute"));
            Attribute attribute = resolveAttribute(attrName);
            if (attribute == null) {
                plugin.getLogger().warning("称号 " + id + " 的属性 " + attrName
                        + " 无效（请使用 1.21.2+ 属性名，如 MAX_HEALTH/ATTACK_DAMAGE/MOVEMENT_SPEED），已跳过");
                continue;
            }
            double amount = asDouble(m.get("amount"), 1.0);
            AttributeModifier.Operation operation = resolveOperation(String.valueOf(m.get("operation")));
            EquipmentSlotGroup slot = resolveSlot(String.valueOf(m.get("slot")));
            attributes.add(new AttributeSpec(attribute, amount, operation, slot));
        }
        return new TitleBuff(potions, attributes);
    }

    /**
     * 属性名解析：从 1.21 属性注册表（Registry.ATTRIBUTE）解析。
     * 配置中写现代枚举名（MAX_HEALTH）或注册表键名（max_health）均可——统一转小写后
     * 即为 1.21.2+ 的注册表键。不使用已弃用的 Attribute.valueOf(String)。
     */
    private Attribute resolveAttribute(String name) {
        if (name == null || name.equals("null")) return null;
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name.toLowerCase()));
    }

    /** Operation 解析：仅使用现代枚举常量，不使用已弃用的 int 常量。 */
    private AttributeModifier.Operation resolveOperation(String name) {
        if (name == null || name.equals("null")) return AttributeModifier.Operation.ADD_NUMBER;
        try {
            return AttributeModifier.Operation.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("属性操作 " + name + " 无效，使用默认 ADD_NUMBER");
            return AttributeModifier.Operation.ADD_NUMBER;
        }
    }

    private EquipmentSlotGroup resolveSlot(String name) {
        if (name == null || name.equals("null")) return EquipmentSlotGroup.ANY;
        switch (name.toLowerCase()) {
            case "hand": return EquipmentSlotGroup.HAND;
            case "mainhand": return EquipmentSlotGroup.MAINHAND;
            case "offhand": return EquipmentSlotGroup.OFFHAND;
            case "feet": return EquipmentSlotGroup.FEET;
            case "legs": return EquipmentSlotGroup.LEGS;
            case "chest": return EquipmentSlotGroup.CHEST;
            case "head": return EquipmentSlotGroup.HEAD;
            case "armor": return EquipmentSlotGroup.ARMOR;
            case "body": return EquipmentSlotGroup.BODY;
            case "any":
            default:
                return EquipmentSlotGroup.ANY;
        }
    }

    private ParticleConfig parseParticle(String id, ConfigurationSection s) {
        if (s == null) return null;
        String typeName = s.getString("type");
        if (typeName == null) return null;
        Particle type;
        try {
            // org.bukkit.Particle 枚举，覆盖 1.21 全部粒子
            type = Particle.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("称号 " + id + " 的粒子 " + typeName + " 无效，已跳过");
            return null;
        }
        int count = s.getInt("count", 3);
        double offset = s.getDouble("offset", 0.3);
        double extra = s.getDouble("extra", 0.0);
        Particle.DustOptions dust = null;
        if (type == Particle.DUST) {
            // DUST 类粒子必须提供颜色数据（DustOptions）
            String colorHex = s.getString("data-color");
            if (colorHex == null) {
                plugin.getLogger().warning("称号 " + id + " 的粒子 " + typeName + " 需要 data-color(#RRGGBB)，已跳过");
                return null;
            }
            try {
                int rgb = Integer.parseInt(colorHex.replace("#", "").trim(), 16);
                float size = (float) s.getDouble("data-size", 1.0);
                dust = new Particle.DustOptions(Color.fromRGB(rgb), size);
            } catch (Exception e) {
                plugin.getLogger().warning("称号 " + id + " 的粒子颜色 " + colorHex + " 无效，已跳过");
                return null;
            }
        }
        return new ParticleConfig(type, count, offset, extra, dust);
    }

    // ---------- GUI（gui.yml） ----------

    /** 商店 GUI 布局（gui.yml 的 shop 段）。 */
    public GuiLayout getShopLayout() { return parseLayout("shop"); }

    /** 称号仓库 GUI 布局（gui.yml 的 chest 段）。 */
    public GuiLayout getChestLayout() { return parseLayout("chest"); }

    /** 商店 GUI 标题（支持 & 颜色代码）。 */
    public String getShopTitle() { return guiConfig.getString("shop-title", "&8称号商店"); }

    /** 称号仓库 GUI 标题（支持 & 颜色代码）。 */
    public String getChestTitle() { return guiConfig.getString("chest-title", "&8称号仓库"); }

    /** 商店 GUI 样式（页码指示器/按钮物品/装饰玻璃板）。 */
    public GuiStyle getShopStyle() { return parseStyle("shop"); }

    /** 称号仓库 GUI 样式。 */
    public GuiStyle getChestStyle() { return parseStyle("chest"); }

    private GuiStyle parseStyle(String path) {
        ConfigurationSection s = guiConfig.getConfigurationSection(path);
        int indicatorSlot = clamp(s == null ? 49 : s.getInt("page-indicator-slot", 49), 0, 53, 49);
        Material indicator = material(s == null ? null : s.getString("page-indicator-item", "NETHER_STAR"),
                Material.NETHER_STAR, path + ".page-indicator-item");
        String indicatorName = s == null ? "&e%page% / %pages%"
                : s.getString("page-indicator-name", "&e%page% / %pages%");
        Material prev = material(s == null ? null : s.getString("prev-item", "ARROW"), Material.ARROW, path + ".prev-item");
        Material next = material(s == null ? null : s.getString("next-item", "ARROW"), Material.ARROW, path + ".next-item");
        Material close = material(s == null ? null : s.getString("close-item", "BARRIER"), Material.BARRIER, path + ".close-item");
        GuiDecoration decoration = parseDecoration(s == null ? null : s.getConfigurationSection("decoration"));
        return new GuiStyle(indicatorSlot, indicator, indicatorName, prev, next, close, decoration);
    }

    private GuiDecoration parseDecoration(ConfigurationSection s) {
        if (s == null) {
            // 未配置 decoration 段：默认槽位 46/47/51/52 + 灰色玻璃板 + 空格名称/lore
            return new GuiDecoration(List.of(46, 47, 51, 52), Material.GRAY_STAINED_GLASS_PANE, " ", List.of(" "));
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot : s.getIntegerList("slots")) {
            if (slot >= 0 && slot <= 53) slots.add(slot);
        }
        if (slots.isEmpty() && !s.isList("slots")) {
            // 未配置 slots 键：使用默认槽位；显式配置 slots: [] 表示不装饰
            slots.addAll(List.of(46, 47, 51, 52));
        }
        Material material = material(s.getString("material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE, "decoration.material");
        String name = s.getString("name", " ");
        List<String> lore = new ArrayList<>();
        Object loreObj = s.get("lore");
        if (loreObj instanceof List<?> list) {
            for (Object o : list) lore.add(String.valueOf(o));
        } else if (loreObj instanceof String str) {
            lore.add(str);
        } else {
            lore.add(" ");
        }
        return new GuiDecoration(slots, material, name, lore);
    }

    /** 解析材质名，无效时回退默认并告警。 */
    private Material material(String name, Material def, String configKey) {
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            plugin.getLogger().warning("配置项 " + configKey + " 的材质 " + name + " 无效，使用默认 " + def);
            return def;
        }
        return m;
    }

    private GuiLayout parseLayout(String path) {
        ConfigurationSection s = guiConfig.getConfigurationSection(path);
        int start = clamp(s == null ? 0 : s.getInt("items-start-slot", 0), 0, 53, 0);
        int count = clamp(s == null ? 45 : s.getInt("items-slots", 45), 1, 54 - start, 45);
        int prev = clamp(s == null ? 48 : s.getInt("prev-slot", 48), 0, 53, 48);
        int close = clamp(s == null ? 53 : s.getInt("close-slot", 53), 0, 53, 53);
        int next = clamp(s == null ? 50 : s.getInt("next-slot", 50), 0, 53, 50);
        int head = clamp(s == null ? 45 : s.getInt("player-head-slot", 45), 0, 53, 45);
        return new GuiLayout(start, count, prev, close, next, head);
    }

    private static int clamp(int value, int min, int max, int def) {
        return (value < min || value > max) ? def : value;
    }

    // ---------- 通用读取 ----------

    public StorageConfig getStorageConfig() {
        ConfigurationSection s = config.getConfigurationSection("storage");
        String type = s == null ? "sqlite" : s.getString("type", "sqlite");
        ConfigurationSection m = s == null ? null : s.getConfigurationSection("mysql");
        ConfigurationSection p = s == null ? null : s.getConfigurationSection("pool");
        return new StorageConfig(
                type,
                m == null ? "mysql" : m.getString("driver", "mysql"),
                m == null ? "localhost" : m.getString("host", "localhost"),
                m == null ? 3306 : m.getInt("port", 3306),
                m == null ? "titlesystem" : m.getString("database", "titlesystem"),
                m == null ? "root" : m.getString("username", "root"),
                m == null ? "" : m.getString("password", ""),
                m != null && m.getBoolean("use-ssl", false),
                p == null ? 10 : p.getInt("maximum-pool-size", 10),
                p == null ? 2 : p.getInt("minimum-idle", 2),
                p == null ? 5000L : p.getLong("connection-timeout-ms", 5000L),
                p == null ? 600000L : p.getLong("idle-timeout-ms", 600000L),
                p == null ? 1800000L : p.getLong("max-lifetime-ms", 1800000L));
    }

    public String getLanguage() { return config.getString("language", "zh_CN"); }
    public boolean isAutoCleanEnabled() { return config.getBoolean("auto-clean.enabled", true); }
    public long getAutoCleanIntervalSeconds() { return config.getLong("auto-clean.interval-seconds", 60); }
    public long getParticleTaskInterval() { return Math.max(1L, config.getLong("particle-task-interval", 20L)); }

    public ConfiguredTitle getTitle(String id) { return titles.get(id); }
    public Map<String, ConfiguredTitle> getTitles() { return Collections.unmodifiableMap(titles); }

    // ---------- 私有工具 ----------

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { }
        }
        return def;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) return s.trim().equalsIgnoreCase("true");
        return def;
    }
}
