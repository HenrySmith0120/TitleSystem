# 更新日志 (Changelog)

本文档记录 TitleSystem（Paper 1.21.8 称号系统）的版本更新，采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/) 风格。

## [1.0.0] - 当前版本

### 最新变更：动态驱动加载（Paper libraries）

- **feat**：数据库相关依赖（HikariCP 6.3.0 / SQLite 驱动 / MySQL 官方驱动 / MariaDB 客户端）不再打进插件 jar，改为 `provided` 作用域 + `plugin.yml` 的 `libraries` 声明，由 Paper 启动时自动解析下载并缓存至服务端根目录 `libraries/` 文件夹（支持离线预置）；插件 jar 由 5.74MB 缩减至约 77KB。
- **feat**：新增 `storage.mysql.driver` 配置项（`mysql` 官方驱动，默认 / `mariadb` MariaDB Java Client），实现运行时驱动切换，`/title reload` 生效；驱动类名仅作为 HikariCP 字符串配置传入，**零反射**。
- **chore**：移除 maven-shade-plugin（全部依赖由 Paper/服务端提供）。
- **docs**：README 新增「依赖加载方式（动态驱动 + Paper libraries）」：驱动切换、首次下载缓存位置、完全离线部署（预置 jar 路径）说明。

### 上一版本：驱动名实一致与打包

- **fix**：数据库驱动由 MariaDB Java Client 换回 **MySQL 官方驱动**（mysql-connector-j 8.4.0，`com.mysql.cj.jdbc.Driver`），与 `config.yml` 的 `storage.type: mysql` 名实一致。
- **feat**：HikariCP / SQLite / MySQL 驱动随插件打包（shade），移除 plugin.yml 的 `libraries` 运行时下载声明，部署即用、全程零联网。
- **perf**：SQLite 原生库由 24 个平台裁剪为 5 个主流平台（Windows x64 / Linux x64 / Linux arm64 / macOS x64 / macOS arm64），成品 jar 5.74MB；如需全平台支持可删除 pom 中对应 filter。
- **docs**：README「体积说明」同步更新。

## v1.0.0 功能总览

- 称号数据：SQLite / MySQL 双模式，HikariCP 连接池，全 PreparedStatement（无 DROP/TRUNCATE）
- 玩家称号：给予（永久/限时）、移除、清空、穿戴/卸下、过期自动清理（异步定时任务）
- 商店 GUI：分页、Vault 金币购买、已拥有标注、购买权限节点；仓库 GUI：真实剩余时间、点击穿戴/卸下
- GUI 可自定义（gui.yml）：标题（& 颜色代码）、槽位布局、翻页/关闭按钮物品、下界之星页码指示器（%page%/%pages%）、玻璃板装饰、左下角玩家信息头颅（ID/余额/拥有称号数）
- 翻页：上一页/下一页常显，边界页点击提示（多语言 gui.first-page / gui.last-page）
- 称号 Buff：原版药水效果（Registry.EFFECT，兼容 1.21 新效果）+ 属性加成（AttributeModifier 现代 API，1.21.2+ 属性名）
- 粒子特效：内置原版粒子引擎（Particle 枚举，DUST 支持 RGB 颜色），实体调度器循环（Folia 兼容）
- 展示：不内置聊天/Tab/头顶显示，通过 PlaceholderAPI 变量（%titlesystem_title% 等）接入 ChatControl/TAB 等插件
- 多语言：messages_zh.yml / messages_en.yml（自动合并升级）；配置文件分文件管理：config.yml / gui.yml / titles.yml（旧配置自动迁移）
- 调度：全部使用 Paper 现代调度器（TaskUtils，Folia 兼容），无 BukkitScheduler 调用
- 安全：无外部网络请求（插件代码）、无反射、无 NMS、无隐藏命令、全 PreparedStatement、零弃用 API
