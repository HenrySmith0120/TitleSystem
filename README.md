# TitleSystem 称号系统（Paper 1.21.8）

开源、无混淆、可审计的 Minecraft 称号系统插件，功能对标 PlayerTitle，面向 **Paper 1.21.8** 及其分支（Leaves 等，基于 Paper API 的实现）开发。

- 包名：`com.henry.title`
- API 版本：`1.21.11`（Paper API 1.21.11 / Minecraft 1.21.11）
- 构建要求：**JDK 25** + Gradle（wrapper 已内置）
- 许可证：MIT

## 功能特性

- 称号数据存储：**SQLite / MySQL 双模式**（HikariCP 连接池，config.yml 一键切换）
- 数据模型：玩家 UUID、称号 ID、获得时间、过期时间（支持永久/限时）、是否激活
- 管理员命令：give / remove / clear / list / reload
- 玩家命令：shop（商店 GUI）/ chest（仓库 GUI）
- 商店 GUI：分页展示、名称/描述/价格、Vault 金币购买、已拥有标注、自动存入仓库
- 仓库 GUI：已拥有称号列表、点击穿戴/卸下、显示**真实**获得时间与剩余时间（不显示商店营销文案）
- GUI 可自定义：商店与仓库的标题（& 颜色代码）、槽位布局、翻页/关闭按钮物品、下界之星页码指示器（%page%/%pages%）、玻璃板装饰均可通过 `gui.yml` 配置
- 称号展示：插件**不内置**聊天/Tab/头顶显示，统一通过 PlaceholderAPI 变量（%titlesystem_title% 等）交给聊天/Tab 插件渲染
- 称号 Buff：原版药水效果（PotionEffect）+ 属性加成（AttributeModifier），佩戴生效、卸下移除
- 粒子特效：内置原版粒子引擎（Particle 枚举，兼容 1.21 新粒子，DUST 类支持 RGB 颜色）
- 自动清理：异步定时任务清理过期称号并移除 Buff
- 多语言：messages_zh.yml / messages_en.yml，由管理员选择
- PlaceholderAPI：%titlesystem_title% / %titlesystem_title_id% / %titlesystem_owned_count%

## 快速安装

1. 将 `target/TitleSystem-1.0.0.jar` 放入服务端 `plugins/` 目录（jar 约 80KB；数据库驱动与连接池由 Paper 在**首次启动时自动下载缓存一次**，详见下文「依赖加载方式」）；
2. （可选）安装 [Vault](https://www.spigotmc.org/resources/vault.34315/) **以及**一个经济插件（如 [EssentialsX](https://essentialsx.net/)、CMI 等）→ 启用金币购买。
   > 注意：Vault 只是 API 桥梁，不提供经济数据；只装 Vault 时插件会提示「已检测到 Vault，但未发现经济服务」。
   > 经济插件晚于本插件启用也没关系——购买时会惰性重试检测，并自动接上。
3. （可选）安装 [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) → 启用 PAPI 占位符；
4. 启动服务端，按需编辑 `plugins/TitleSystem/` 下的配置文件后执行 `/title reload`：
   - `config.yml`（主配置：语言/存储/自动清理/粒子周期）
   - `gui.yml`（GUI 标题/布局/页码指示器/装饰）
   - `titles.yml`（称号定义）

## 自行构建

```bash
gradlew build
# 产物: build/libs/*-all.jar（shadow 已内嵌并重定位 HikariCP）
```

依赖仓库全部公开可审计：

| 依赖 | 来源 | 说明 |
| --- | --- | --- |
| io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT | Paper 官方仓库 | provided，服务端提供 |
| net.kyori:adventure-*:4.24.0 | Maven Central | provided，Paper 自带 |
| com.github.MilkBowl:VaultAPI:1.7.1 | JitPack（构建官方 MilkBowl/VaultAPI 公开源码） | 仅编译期，不打包 |
| me.clip:placeholderapi:2.11.6 | PlaceholderAPI 官方仓库 | provided |
| HikariCP 6.3.0 | Maven Central | implementation，shadow 打包并重定位到 com.henry.title.libs.hikari |
| sqlite-jdbc / mysql-connector-j（SQLite / MySQL 驱动） | Maven Central | 不声明——由 Paper 服务端内置提供 |

> 若坚持不使用 JitPack：删除 build.gradle 中 VaultAPI 依赖与 `hook/EconomyHook.java`、`gui/ShopGui.java` 中的经济调用即可（免费称号不受影响）。

### 依赖加载方式（驱动随服务端内置 + HikariCP shadow 打包）

- **SQLite / MySQL 驱动**：Paper 服务端已内置（官方 paper-server 构建文件包含 sqlite-jdbc 与 mysql-connector-j 的 runtimeOnly 依赖），插件直接使用，零打包、零下载；
- **HikariCP 连接池**：由 Gradle Shadow 打包进插件 jar，并重定位到 `com.henry.title.libs.hikari`（避免与其他插件冲突）；
- **驱动选择**：`storage.type` 支持 `sqlite | mysql` 两种平级模式，`/title reload` 生效；驱动类名仅作为 HikariCP 的字符串配置传入，零反射；

## 命令与权限

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| /title shop | title.user | 打开称号商店 |
| /title chest | title.user | 打开称号仓库 |
| /title give <玩家> <称号ID> [天数] | title.admin | 给予称号（缺省天数=永久） |
| /title remove <玩家> <称号ID> | title.admin | 移除称号 |
| /title clear <玩家> | title.admin | 清空玩家全部称号 |
| /title list [页] | title.admin | 列出所有称号 |
| /title reload | title.admin | 重载配置 |

### GUI 配置（gui.yml）

```yaml
shop-title: "&8称号商店"   # GUI 标题（支持 & 颜色代码）
chest-title: "&8称号仓库"
shop:            # 商店 GUI（chest 结构相同）
  items-start-slot: 0      # 称号物品起始槽位
  items-slots: 45          # 每页最多称号数量
  player-head-slot: 45     # 左下角玩家信息头颅（ID/余额/拥有称号数）
  prev-slot: 48            # 上一页按钮
  next-slot: 50            # 下一页按钮
  close-slot: 53           # 关闭按钮（右下角）
  prev-item: ARROW         # 上一页按钮物品（Material 枚举名）
  next-item: ARROW
  close-item: BARRIER
  page-indicator-slot: 49  # 页码指示器槽位（上一页与下一页之间）
  page-indicator-item: NETHER_STAR          # 页码指示器物品（下界之星）
  page-indicator-name: "&e第 &f%page% &e/ &f%pages% &e页"  # %page%=当前页 %pages%=总页数
  decoration:              # 玻璃板装饰
    slots: [46, 47, 51, 52]                 # 装饰槽位（留空 [] 表示不装饰）
    material: GRAY_STAINED_GLASS_PANE
    name: " "              # 默认一个空格
    lore: " "              # 默认一个空格
```

> 按钮槽位相互冲突时以配置顺序为准。上一页/下一页按钮始终显示；点击边界页会提示「已经是第一页/最后一页」（gui.first-page / gui.last-page 多语言键）。

购买权限节点 `title.buy.<称号ID>`（示例称号已内置），称号配置不填 permission 则购买无需权限。全部命令与权限均在 plugin.yml 声明，无隐藏命令。

### 称号展示（PlaceholderAPI）

插件**不内置**聊天/Tab/头顶显示，称号展示完全交给 PlaceholderAPI 变量，由聊天/Tab 插件渲染：

- `%titlesystem_title%`：当前穿戴称号的展示文本（如 `&6[&eVIP&6]`，ChatControl / TAB 的颜色系统可直接解析 & 颜色码）
- `%titlesystem_title_id%`：当前称号 ID（如 `vip`）
- `%titlesystem_owned_count%`：拥有的称号数量

示例——ChatControl 的聊天格式配置：

```yaml
format: "%titlesystem_title% <player_name> %message%"
```

示例——TAB 插件：把 `%titlesystem_title%` 加入 tagprefix / customtabname / belowname 等即可实现 Tab 前缀与头顶称号。

## 安全与审计承诺

1. **无外部网络请求**：插件代码不发起任何 HTTP 请求；SQLite 模式全程零网络（MySQL 连接目标为管理员在 config.yml 中显式配置的数据库主机）。
2. **无反射 / 无远程代码**：不使用反射加载任何类，不使用 URLClassLoader，不使用 Runtime.exec / ProcessBuilder；Vault / PlaceholderAPI 均为显式插件检测 + 官方 API 调用。
3. **无隐藏命令**：所有命令与权限节点均在 plugin.yml 声明。
4. **全 PreparedStatement**：所有数据库 DML 一律参数化查询，无 DROP TABLE / TRUNCATE；删除仅按 UUID/称号ID 精确执行且命令层已做权限校验。
5. **全本地资源**：配置与语言文件全部本地读取，不下载任何远程内容。
6. **零弃用 API**：以 -Xlint:deprecation,removal 编译 0 警告（已在本仓库验证）。

## 技术实现说明（Paper 1.21 现代 API）

- 文本消息：全部 Adventure `Component`，`Player#sendMessage(Component)`；配置文本用 & 颜色代码经 LegacyComponentSerializer 解析。
- 属性加成：`new AttributeModifier(NamespacedKey, double, Operation, EquipmentSlotGroup)`；Operation 枚举（ADD_NUMBER/ADD_SCALAR/MULTIPLY_SCALAR_1，无 int 常量）；属性从 `Registry.ATTRIBUTE` 解析（1.21.2+ 名称 MAX_HEALTH/ATTACK_DAMAGE 等）。
- 药水效果：`Registry.EFFECT` 解析（兼容 1.21 新效果 INFESTED/OOZING/WEAVING/WIND_CHARGED 等）。
- 粒子：`Particle` 枚举 + `World#spawnParticle` 泛型重载（DustOptions 颜色）。
- CustomModelData：1.21.4+ 数据组件 API（`ItemStack#setData` + `DataComponentTypes.CUSTOM_MODEL_DATA`），不使用弃用的 `ItemMeta#setCustomModelData(Integer)`。
- 插件版本读取：`Plugin#getPluginMeta()`（不使用弃用的 getDescription()）。
- 数据库：HikariCP 连接池（shadow 打包并重定位）+ 全 PreparedStatement；`storage.type` 平级选择 SQLite / MySQL（驱动均由 Paper 服务端内置）。

## 已知局限

- removePotionEffect 按效果类型移除：若其他插件给了同类型药水效果，卸称号时会一并移除（原版药水机制限制）。

## 可选扩展

- **SuperTrails / PlayerParticles 粒子联动**：插件启动时会自动检测这两个插件是否存在。本版本使用内置原版粒子引擎（默认回退）；如需深度联动，可参照 `hook/EconomyHook.java` 的"显式检测 + 官方 API"模式，添加其官方 API 依赖并实现一个 Hook 类（不涉及反射）。
- **PlayerPoints 点数购买**：pom.xml 中已注释依赖片段，取消注释后按同样模式实现 PointHook 即可。

## 调度器工具类（兼容 Folia）

`com.henry.title.util.TaskUtils` 封装了 Paper 现代调度器（纯 Paper API、无反射、Paper 1.21+）：

- 全局调度器：`TaskUtils.run / runLater`（主线程，tick 单位）
- 异步调度器：`TaskUtils.runAsync / runLaterAsync / runTimerAsync`（Paper 异步线程池，勿触碰实体）
- 实体调度器：`TaskUtils.runEntity / runEntityTimer`（跟随实体 tick 线程，实体卸载任务自动作废）
- GUI 心跳任务：`TaskUtils.runTimerAsync(Player, id, ...)`——菜单打开期间定时刷新、菜单关闭自动取消（需先 `new TaskUtils(plugin)` 并 `TaskUtils.setOpenMenuProvider(...)` 注入菜单 ID 提供者）

```java
// 启动时初始化
new TaskUtils(plugin);
TaskUtils.setOpenMenuProvider(player -> MenuManager.getOpenMenuId(player));
// 玩家打开 shop 菜单时启动心跳刷新；菜单关闭自动取消
TaskUtils.runTimerAsync(player, "shop", () -> MenuManager.refresh(player, "shop"), 0L, 20L);
```

旧版 `BukkitScheduler#runTask*` 在 Folia 上不可用，请统一使用本工具类；插件卸载时 Paper 会自动取消全局/异步任务，实体任务随实体退役自动清理。

插件内部已全部迁移到 TaskUtils：称号粒子循环=实体调度器、过期称号自动清理=异步调度器、数据加载回调=实体/全局调度器（Folia 兼容，项目内已无 BukkitScheduler 调用）。

## 项目结构

```
TitleSystem/
├── pom.xml
├── src/main/java/com/henry/title/
│   ├── TitleSystem.java          # 主类
│   ├── command/TitleCommand.java # /title 命令（plugin.yml 声明）
│   ├── config/                   # ConfigManager / MessageManager / StorageConfig
│   ├── data/                     # DatabaseManager（HikariCP+PreparedStatement）/ TitleEntry
│   ├── gui/                      # ShopGui / ChestGui / GuiHolder
│   ├── hook/                     # EconomyHook(Vault) / PlaceholderHook(PAPI)
│   ├── listener/                 # PlayerListener / GuiListener
│   ├── manager/                  # TitleManager / BuffManager / ParticleManager
│   ├── model/                    # ConfiguredTitle / TitleBuff / AttributeSpec / ParticleConfig / GuiLayout / GuiStyle / GuiDecoration
│   └── util/                     # TextUtil / GuiUtil / TaskUtils
└── src/main/resources/
    ├── plugin.yml
    ├── config.yml                # 主配置（语言/存储/自动清理/粒子周期）
    ├── gui.yml                   # GUI 标题/布局/页码指示器/装饰
    ├── titles.yml                # 称号定义（4 个示例称号）
    └── messages/                 # messages_zh.yml / messages_en.yml
```