package com.henry.title.data;

import java.util.UUID;

/**
 * 玩家称号记录（对应 title_players 表一行）。
 * 数据模型：玩家 UUID、称号 ID、获得时间、过期时间（-1=永久）、是否激活。
 */
public final class TitleEntry {

    private final UUID playerUuid;
    private final String titleId;
    private final long acquireTime; // 毫秒时间戳
    private final long expireTime;  // -1 = 永久
    private final boolean active;

    public TitleEntry(UUID playerUuid, String titleId, long acquireTime, long expireTime, boolean active) {
        this.playerUuid = playerUuid;
        this.titleId = titleId;
        this.acquireTime = acquireTime;
        this.expireTime = expireTime;
        this.active = active;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getTitleId() { return titleId; }
    public long getAcquireTime() { return acquireTime; }
    public long getExpireTime() { return expireTime; }
    public boolean isActive() { return active; }
    public boolean isPermanent() { return expireTime < 0; }
    public boolean isExpired(long now) { return expireTime >= 0 && expireTime < now; }
}
