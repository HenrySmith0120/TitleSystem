package com.henry.title.data;

import com.henry.title.TitleSystem;
import com.henry.title.config.StorageConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 数据库管理：HikariCP 连接池 + SQLite / MySQL 双模式（config.yml 切换）。
 * <p>
 * 安全约定（可审计）：
 * 1. 所有 DML 语句一律使用 PreparedStatement（防 SQL 注入）；
 * 2. 仅包含 CREATE TABLE IF NOT EXISTS 建表语句，不含 DROP TABLE / TRUNCATE 等危险语句；
 * 3. 删除操作仅允许按 player_uuid / title_id 精确删除（管理员命令触发且已做权限校验）；
 * 4. SQLite 为本地文件模式，不产生任何网络连接；MySQL 仅连接管理员在 config.yml 中显式配置的主机。
 */
public final class DatabaseManager {

    // 建表语句（固定常量，无注入面）
    private static final String SQL_CREATE_PLAYER_TITLES =
            "CREATE TABLE IF NOT EXISTS title_players (" +
                    " player_uuid  VARCHAR(36) NOT NULL," +
                    " title_id     VARCHAR(64) NOT NULL," +
                    " acquire_time BIGINT NOT NULL," +
                    " expire_time  BIGINT NOT NULL," + // -1 = 永久
                    " active       INT NOT NULL DEFAULT 0," +
                    " PRIMARY KEY (player_uuid, title_id))";

    private final TitleSystem plugin;
    private final StorageConfig storage;
    private boolean mysql;
    private HikariDataSource dataSource;

    public DatabaseManager(TitleSystem plugin, StorageConfig storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /**
     * 初始化连接池与建表。失败抛出异常由主类处理。
     */
    public void init() throws SQLException {
        HikariConfig hc = new HikariConfig();
        if (storage.type().equalsIgnoreCase("mysql")) {
            // 驱动按 config.yml 的 storage.mysql.driver 选择（均由 Paper libraries 动态加载，无需反射）：
            //   mysql（默认）= MySQL 官方驱动 com.mysql.cj.jdbc.Driver
            hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hc.setJdbcUrl("jdbc:mysql://" + storage.host() + ":" + storage.port() + "/" + storage.database()
                    + "?useSSL=" + storage.useSsl()
                    + "&allowPublicKeyRetrieval=true"
                    + "&autoReconnect=true"
                    + "&characterEncoding=utf8");

            hc.setUsername(storage.username());
            hc.setPassword(storage.password());
            this.mysql = true;
        } else {
            File folder = plugin.getDataFolder();
            if (!folder.exists() && !folder.mkdirs()) {
                throw new SQLException("无法创建插件数据目录: " + folder.getAbsolutePath());
            }
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setJdbcUrl("jdbc:sqlite:" + new File(folder, "titles.db").getAbsolutePath());
            this.mysql = false;
        }
        hc.setPoolName("TitleSystemPool");
        hc.setMaximumPoolSize(storage.maxPool());
        hc.setMinimumIdle(storage.minIdle());
        hc.setConnectionTimeout(storage.connTimeout());
        hc.setIdleTimeout(storage.idleTimeout());
        hc.setMaxLifetime(storage.maxLifetime());
        this.dataSource = new HikariDataSource(hc);

        createTables();
        if (!mysql) {
            // SQLite：启用 WAL 提升并发读写性能
            try (Connection c = dataSource.getConnection();
                 PreparedStatement ps = c.prepareStatement("PRAGMA journal_mode=WAL;")) {
                ps.execute();
            }
        }
    }

    private void createTables() throws SQLException {
        // DDL 同样通过 PreparedStatement 执行（静态语句，参数占位符仅在 DML 中使用）
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps1 = c.prepareStatement(SQL_CREATE_PLAYER_TITLES)) {
            ps1.executeUpdate();
        }
    }

    /**
     * 读取玩家全部称号记录。
     */
    public List<TitleEntry> loadPlayerTitles(UUID uuid) throws SQLException {
        String sql = "SELECT title_id, acquire_time, expire_time, active FROM title_players WHERE player_uuid = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<TitleEntry> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new TitleEntry(uuid, rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getInt(4) != 0));
                }
                return list;
            }
        }
    }

    /**
     * 插入或刷新称号（已拥有则刷新获得时间与有效期）。
     */
    public void saveTitle(UUID uuid, String titleId, long acquireTime, long expireTime) throws SQLException {
        String sql = mysql
                ? "INSERT INTO title_players (player_uuid, title_id, acquire_time, expire_time, active) " +
                  "VALUES (?, ?, ?, ?, 0) " +
                  "ON DUPLICATE KEY UPDATE acquire_time = VALUES(acquire_time), expire_time = VALUES(expire_time)"
                : "INSERT INTO title_players (player_uuid, title_id, acquire_time, expire_time, active) " +
                  "VALUES (?, ?, ?, ?, 0) " +
                  "ON CONFLICT(player_uuid, title_id) DO UPDATE SET " +
                  "acquire_time = excluded.acquire_time, expire_time = excluded.expire_time";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, titleId);
            ps.setLong(3, acquireTime);
            ps.setLong(4, expireTime);
            ps.executeUpdate();
        }
    }

    /**
     * 删除单个称号记录。
     */
    public void deleteTitle(UUID uuid, String titleId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM title_players WHERE player_uuid = ? AND title_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, titleId);
            ps.executeUpdate();
        }
    }

    /**
     * 清空某玩家全部称号记录（仅管理员 /title clear 触发，已做权限校验）。
     */
    public void clearPlayerTitles(UUID uuid) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM title_players WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        }
    }

    /**
     * 事务：将指定称号设为激活，其余全部取消激活。titleId 为 null 表示全部取消。
     */
    public void setActiveTitle(UUID uuid, String titleId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE title_players SET active = 0 WHERE player_uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                if (titleId != null) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE title_players SET active = 1 WHERE player_uuid = ? AND title_id = ?")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, titleId);
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * 清理过期称号（事务：先查出过期行再逐行删除）。
     * 返回被删除的 (player_uuid, title_id) 列表，供主线程清理显示与 Buff。
     */
    public List<String[]> cleanExpiredTitles(long now) throws SQLException {
        List<String[]> removed = new ArrayList<>();
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<String[]> rows = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT player_uuid, title_id FROM title_players WHERE expire_time > 0 AND expire_time < ?")) {
                    ps.setLong(1, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new String[]{rs.getString(1), rs.getString(2)});
                        }
                    }
                }
                if (!rows.isEmpty()) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM title_players WHERE player_uuid = ? AND title_id = ?")) {
                        for (String[] row : rows) {
                            ps.setString(1, row[0]);
                            ps.setString(2, row[1]);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                    removed.addAll(rows);
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return removed;
    }

    /**
     * 关闭连接池。
     */
    public void close() {
        if (dataSource != null) dataSource.close();
    }
}
