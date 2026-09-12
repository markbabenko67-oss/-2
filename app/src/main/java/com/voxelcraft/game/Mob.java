package com.voxelcraft.game;

import java.util.Random;

/**
 * A wandering passive mob (cow / pig / sheep).
 * Simple physics: gravity + ground, random wandering with simple obstacle check.
 */
public final class Mob {

    public enum Kind { COW, PIG, SHEEP }

    public double x, y, z;
    public double vy;
    public float yaw;
    public boolean onGround;
    public final Kind kind;
    public final float speed;
    public final boolean hopper;

    private float wanderTimer;
    private final Random rnd = new Random();

    public Mob(double x, double y, double z, int seed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.wanderTimer = 1f + rnd.nextFloat() * 3f;
        int k = seed & 0x7fffffff;
        Kind[] all = Kind.values();
        this.kind = all[k % all.length];
        this.speed = 1.1f + (k % 7) * 0.18f;
        this.hopper = (k & 0x100) != 0;
        this.yaw = (float) ((k >>> 4) % 3600) / 3600f * 6.2831853f;
    }

    public void update(float dt, World w) {
        wanderTimer -= dt;
        if (wanderTimer <= 0) {
            wanderTimer = 2f + rnd.nextFloat() * 4f;
            yaw = rnd.nextFloat() * 6.2831853f;
        }

        double dx = -Math.sin(yaw) * speed * dt;
        double dz = -Math.cos(yaw) * speed * dt;
        double nx = x + dx;
        double nz = z + dz;
        if (!blocked(nx, nz, w)) {
            x = nx;
            z = nz;
        }

        if (hopper && onGround && rnd.nextFloat() < dt * 3f) {
            vy = 4.0;
            onGround = false;
        }

        vy -= 22 * dt;
        y += vy * dt;

        int by = (int) Math.floor(y - 1e-4);
        byte under = w.getBlock((int) Math.floor(x), by, (int) Math.floor(z));
        if (by >= 0 && by < Chunk.SY && (Blocks.isSolid(under) || Blocks.isWater(under))) {
            y = by + 1;
            vy = 0;
            onGround = true;
        }
    }

    private boolean blocked(double nx, double nz, World w) {
        int ix = (int) Math.floor(nx);
        int iz = (int) Math.floor(nz);
        int by0 = (int) Math.floor(y + 0.2);
        int by1 = (int) Math.floor(y + 0.9);
        for (int by = by0; by <= by1; by++) {
            if (by < 0 || by >= Chunk.SY) continue;
            if (Blocks.isSolid(w.getBlock(ix, by, iz))) return true;
        }
        return false;
    }
}