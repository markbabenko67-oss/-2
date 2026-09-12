package com.voxelcraft.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Infinite voxel world made of chunks.
 */
public final class World {

    public static final int WATER_LEVEL = 17;
    public static final int CHUNK_SIZE = 16;

    public final Noise noise;
    private final HashMap<Long, Chunk> chunks = new HashMap<>();

    public double spawnX = 8.5;
    public double spawnZ = 8.5;
    public double spawnY = 24;

    public World(int seed) {
        noise = new Noise(seed);
    }

    private long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(key(cx, cz));
    }

    public HashMap<Long, Chunk> getChunks() {
        return chunks;
    }

    public byte getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SY) return Blocks.AIR;
        int cx = (int) Math.floorDiv(x, CHUNK_SIZE);
        int cz = (int) Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return Blocks.AIR;
        return c.local(x - cx * CHUNK_SIZE, y, z - cz * CHUNK_SIZE);
    }

    /** Places block and marks affected chunk meshes dirty. */
    public void setBlock(int x, int y, int z, byte b) {
        if (y < 0 || y >= Chunk.SY) return;
        int cx = (int) Math.floorDiv(x, CHUNK_SIZE);
        int cz = (int) Math.floorDiv(z, CHUNK_SIZE);
        Chunk c = chunks.get(key(cx, cz));
        if (c == null) return;
        int lx = x - cx * CHUNK_SIZE;
        int lz = z - cz * CHUNK_SIZE;
        c.setLocal(lx, y, lz, b);
        c.meshDirty = true;
        if (lx == 0) markDirty(cx - 1, cz);
        if (lx == CHUNK_SIZE - 1) markDirty(cx + 1, cz);
        if (lz == 0) markDirty(cx, cz - 1);
        if (lz == CHUNK_SIZE - 1) markDirty(cx, cz + 1);
    }

    private void markDirty(int cx, int cz) {
        Chunk c = chunks.get(key(cx, cz));
        if (c != null) c.meshDirty = true;
    }

    public Chunk ensureChunk(int cx, int cz) {
        Chunk c = chunks.get(key(cx, cz));
        if (c != null) return c;
        c = new Chunk(cx, cz);
        c.generate(this, noise);
        chunks.put(key(cx, cz), c);
        return c;
    }

    /** Rebuilds vertex data for every dirty chunk. */
    public void rebuildDirtyMeshes() {
        for (Chunk c : chunks.values()) {
            if (c.meshDirty) {
                c.mesh(this);
                // invalidate GL upload so the renderer re-uploads the VBO
                c.glStamp = -1;
            }
        }
    }

    /** Unloads chunks far away to keep memory bounded. */
    public void trim(int pcx, int pcz, int keepRadius) {
        ArrayList<Long> remove = null;
        for (HashMap.Entry<Long, Chunk> e : chunks.entrySet()) {
            Chunk c = e.getValue();
            int dx = c.cx - pcx;
            int dz = c.cz - pcz;
            if (dx * dx + dz * dz > keepRadius * keepRadius) {
                if (remove == null) remove = new ArrayList<>();
                remove.add(e.getKey());
            }
        }
        if (remove != null) {
            for (Long k : remove) chunks.remove(k);
        }
    }

    private final ArrayDeque<long[]> loadQueue = new ArrayDeque<>();

    /** Queue: generate all chunks around player within radius that are missing. */
    public void requestAround(double px, double pz, int radius) {
        int pcx = (int) Math.floor(px / CHUNK_SIZE);
        int pcz = (int) Math.floor(pz / CHUNK_SIZE);
        loadQueue.clear();
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int ax = Math.abs(dx), az = Math.abs(dz);
                    if (Math.max(ax, az) != r) continue;
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (getChunk(cx, cz) != null) continue;
                    loadQueue.add(new long[]{cx, cz});
                }
            }
        }
    }

    /** Generates up to {@code budget} queued chunks. */
    public void stepLoading(int budget) {
        int left = budget;
        while (left-- > 0 && !loadQueue.isEmpty()) {
            long[] key = loadQueue.poll();
            ensureChunk((int) key[0], (int) key[1]);
        }
    }

    public boolean isLoadQueueDone() {
        return loadQueue.isEmpty();
    }

    /** Highest solid block at a column (for spawn). */
    public int topSolid(int wx, int wz) {
        for (int y = Chunk.SY - 1; y >= 0; y--) {
            if (Blocks.isSolid(getBlock(wx, y, wz))) return y;
        }
        return 0;
    }

    /**
     * Searches outward from the origin for a dry, buildable spawn column.
     * Returns true if found (and updates spawnX/Y/Z).
     */
    public boolean findSpawn() {
        for (int r = 0; r <= 30; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int wx = 8 + dx;
                    int wz = 8 + dz;
                    int h = Chunk.heightAt(noise, wx, wz);
                    if (h < 19 || h > 42) continue;
                    int cx = (int) Math.floorDiv(wx, CHUNK_SIZE);
                    int cz = (int) Math.floorDiv(wz, CHUNK_SIZE);
                    if (getChunk(cx, cz) == null) ensureChunk(cx, cz);
                    int top = topSolid(wx, wz);
                    if (top >= WATER_LEVEL + 1 && top <= 42) {
                        spawnX = wx + 0.5;
                        spawnZ = wz + 0.5;
                        spawnY = top + 1;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Grid DDA raycast. Returns the first non-air block within max blocks
     * from origin, or null.
     */
    public Hit raycast(double ox, double oy, double oz,
                       double dx, double dy, double dz, double max) {
        double x = Math.floor(ox);
        double y = Math.floor(oy);
        double z = Math.floor(oz);

        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;

        double tMaxX = (dx == 0) ? Double.MAX_VALUE : ((dx > 0 ? (x + 1 - ox) : (ox - x)) / dx);
        double tMaxY = (dy == 0) ? Double.MAX_VALUE : ((dy > 0 ? (y + 1 - oy) : (oy - y)) / dy);
        double tMaxZ = (dz == 0) ? Double.MAX_VALUE : ((dz > 0 ? (z + 1 - oz) : (oz - z)) / dz);

        double tDeltaX = (dx == 0) ? Double.MAX_VALUE : Math.abs(1 / dx);
        double tDeltaY = (dy == 0) ? Double.MAX_VALUE : Math.abs(1 / dy);
        double tDeltaZ = (dz == 0) ? Double.MAX_VALUE : Math.abs(1 / dz);

        int nx = 0, ny = 0, nz = 0;
        double t = 0;

        while (true) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY; t = tMaxY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0;
            } else {
                z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ;
            }
            if (t > max) return null;
            if (y < 0 || y >= Chunk.SY) return null;
            byte b = getBlock((int) x, (int) y, (int) z);
            if (b != Blocks.AIR && !(Blocks.isWater(b) && t < 0.2)) {
                return new Hit((int) x, (int) y, (int) z, nx, ny, nz);
            }
        }
    }
}