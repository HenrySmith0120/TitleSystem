package com.henry.title.config;

/**
 * 存储配置（供 HikariCP 连接池使用）。
 * type = sqlite | mysql；driver = mysql（官方驱动，默认）| mariadb（MariaDB Java Client），仅 mysql 模式有效。
 * sqlite 为本地文件模式，不产生任何网络连接。
 */
public record StorageConfig(String type, String driver, String host, int port, String database,
                            String username, String password, boolean useSsl,
                            int maxPool, int minIdle, long connTimeout,
                            long idleTimeout, long maxLifetime) {
}
