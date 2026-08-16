package com.henry.title.model;

import org.bukkit.Particle;

/**
 * 粒子特效配置（org.bukkit.Particle 枚举，覆盖 1.21 全部粒子）。
 * dust 仅对 DUST/REDSTONE 类粒子非空（DustOptions 数据）。
 */
public record ParticleConfig(Particle type, int count, double offset, double extra, Particle.DustOptions dust) {
}
