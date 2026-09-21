package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;

public class ParticleService {

    public void ring(Location center, double radius, int count) {
        if (center == null || center.getWorld() == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(248, 190, 251), 1.2f);
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location loc = center.clone().add(x, 0.5, z);
            center.getWorld().spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0, 0, dust);
        }
    }
}