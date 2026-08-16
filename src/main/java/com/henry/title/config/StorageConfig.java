package com.henry.title.config;

/**
 * 存储配置（供 HikariCP 连接池使用）。
 * type = sqlite | mysql；sqlite 为本地文件模式，不产生任何网络连接。
 */
public record StorageConfig(String type, String host, int port, String database,
                            String username, String password, boolean useSsl,
                            int maxPool, int minIdle, long connTimeout,
                            long idleTimeout, long maxLifetime) {
}
