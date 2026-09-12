package com.voxelcraft.game;

/**
 * 16x64x16 voxel storage for one horizontal chunk column.
 */
public final class Chunk {

    public static final int SX = 16;
    public static final int SY = 64;
    public static final int SZ = 16;

    public final int cx;
    public final int cz;

    public final byte[] blocks = new byte[SX * SY * SZ];

    public boolean meshDirty = true;

    // CPU vertex data (rebuilt by mesh())
    public float[] solidVerts = new float[0];
    public float[] transparentVerts = new float[0];

    // GL state (owned by the renderer)
    public int solidBufferId = 0;
    public int transparentBufferId = 0;
    public int glStamp = -1;

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    public byte local(int x, int y, int z) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return Blocks.AIR;
        return blocks[index(x, y, z)];
    }

    public void setLocal(int x, int y, int z, byte b) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return;
        blocks[index(x, y, z)] = b;
    }

    /** Fills terrain + trees for this chunk (deterministic, pure functions of coordinates). */
    public void generate(World w, Noise n) {
        for (int lx = 0; lx < SX; lx++) {
            for (int lz = 0; lz < SZ; lz++) {
                int wx = cx * SX + lx;
                int wz = cz * SZ + lz;
                int h = heightAt(n, wx, wz);
                for (int ly = 0; ly < SY; ly++) {
                    int wy = ly;
                    byte b = Blocks.AIR;
                    if (wy == 0) {
                        b = Blocks.BEDROCK;
                    } else if (wy <= h) {
                        boolean carve = false;
                        if (h > 6 && wy < h - 1) {
                            float c = n.noise3D(wx * 0.08f, wy * 0.08f, wz * 0.08f);
                            if (c > 0.60f) carve = true;
                        }
                        if (carve) {
                            b = Blocks.AIR;
                        } else if (wy == h) {
                            b = (h <= 20) ? Blocks.SAND : Blocks.GRASS;
                        } else if (wy >= h - 2) {
                            b = (h <= 20) ? Blocks.SAND : Blocks.DIRT;
                        } else {
                            b = Blocks.STONE;
                        }
                    } else if (wy <= World.WATER_LEVEL && h < World.WATER_LEVEL) {
                        b = Blocks.WATER;
                    }
                    setLocal(lx, ly, lz, b);
                }
            }
        }
        placeTrees(w, n);
    }

    public static int heightAt(Noise n, int wx, int wz) {
        float c = n.fbm2D(wx * 0.004f, wz * 0.004f, 3, 2.0f, 0.5f);  // continents
        float b = n.fbm2D(wx * 0.02f, wz * 0.02f, 3, 2.0f, 0.5f);   // hills
        float d = n.fbm2D(wx * 0.08f, wz * 0.08f, 3, 2.1f, 0.5f);   // detail
        float h = 24f + (c - 0.5f) * 42f + (b - 0.5f) * 9f + (d - 0.5f) * 4f;
        return (int) Math.max(3, Math.min(Chunk.SY - 2, Math.floor(h)));
    }

    /**
     * Deterministic tree per region of 4x4 columns.
     * Returns trunk world X if the column (wx,wz) is a trunk column, else -1.
     */
    public static int treeCenterX(Noise n, int wx, int wz) {
        int rx = (int) Math.floor(wx / 4f);
        int rz = (int) Math.floor(wz / 4f);
        int h = n.hashInt(rx, rz);
        int k = h & 0x7fffffff;
        if (k % 3 != 0) return -1;
        int ox = (k >>> 8) & 3;
        int oz = (k >>> 16) & 3;
        int tx = rx * 4 + ox;
        int tz = rz * 4 + oz;
        if (wx != tx || wz != tz) return -1;
        return tx;
    }

    private static int treeHeight(Noise n, int rx, int rz) {
        int h = n.hashInt(rx, rz);
        int k = h & 0x7fffffff;
        return 4 + ((k >>> 2) & 3);
    }

    private void placeTrees(World w, Noise n) {
        // trunk blocks from columns inside this chunk
        for (int lx = 0; lx < SX; lx++) {
            for (int lz = 0; lz < SZ; lz++) {
                int wx = cx * SX + lx;
                int wz = cz * SZ + lz;
                int tx = treeCenterX(n, wx, wz);
                if (tx < 0) continue;
                int h = heightAt(n, wx, wz);
                if (h < 22 || h > 46) continue;
                int th = treeHeight(n, (int) Math.floor(wx / 4f), (int) Math.floor(wz / 4f));
                int topY = h + th;
                for (int y = h + 1; y <= topY; y++) {
                    if (local(lx, y, lz) == Blocks.AIR) setLocal(lx, y, lz, Blocks.WOOD);
                }
            }
        }
        // leaves: scan extended range (trunk columns in neighbor chunks may reach into this one)
        int minX = cx * SX - 3;
        int maxX = cx * SX + SX + 3;
        int minZ = cz * SZ - 3;
        int maxZ = cz * SZ + SZ + 3;
        for (int wx = minX; wx <= maxX; wx++) {
            for (int wz = minZ; wz <= maxZ; wz++) {
                int tx = treeCenterX(n, wx, wz);
                if (tx < 0) continue;
                int h = heightAt(n, wx, wz);
                if (h < 22 || h > 46) continue;
                setLeaves(w, n, wx, wz, h, treeHeight(n, (int) Math.floor(wx / 4f), (int) Math.floor(wz / 4f)));
            }
        }
    }

    private void setLeaves(World w, Noise n, int tx, int tz, int h, int th) {
        int centerY = h + th + 1;
        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                    int x = tx + dx;
                    int y = centerY + dy - 1;
                    int z = tz + dz;
                    float keep = n.hash01(x, y, z);
                    if (keep > 0.78f) continue;
                    setIfAir(w, x, y, z, Blocks.LEAVES);
                }
            }
        }
        // cap
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = tx + dx;
                int y = centerY + 2;
                int z = tz + dz;
                float keep = n.hash01(x, y, z);
                if (keep > 0.7f) continue;
                setIfAir(w, x, y, z, Blocks.LEAVES);
            }
        }
    }

    private void setIfAir(World w, int wx, int wy, int wz, byte b) {
        int lx = wx - cx * SX;
        int lz = wz - cz * SZ;
        if (lx < 0 || lx >= SX || lz < 0 || lz >= SZ) return;
        if (wy < 1 || wy >= SY) return;
        if (local(lx, wy, lz) == Blocks.AIR) setLocal(lx, wy, lz, b);
    }

    /** Rebuilds CPU vertex arrays from current block data. */
    public void mesh(World w) {
        FloatList solid = new FloatList(4096);
        FloatList transparent = new FloatList(1024);
        for (int lx = 0; lx < SX; lx++) {
            for (int lz = 0; lz < SZ; lz++) {
                for (int ly = 0; ly < SY; ly++) {
                    byte b = local(lx, ly, lz);
                    if (b == Blocks.AIR) continue;
                    int wx = cx * SX + lx;
                    int wy = ly;
                    int wz = cz * SZ + lz;
                    for (int f = 0; f < 6; f++) {
                        byte nb = neighbor(w, wx, wy, wz, f);
                        if (!Blocks.faceVisible(b, nb)) continue;
                        FloatList out = Blocks.isOpaque(b) ? solid : transparent;
                        MeshBuilder.addCubeFace(out, wx, wy, wz, f, b);
                    }
                }
            }
        }
        solidVerts = solid.toArray();
        transparentVerts = transparent.toArray();
        meshDirty = false;
    }

    private static byte neighbor(World w, int wx, int wy, int wz, int face) {
        switch (face) {
            case Blocks.FACE_TOP: return w.getBlock(wx, wy + 1, wz);
            case Blocks.FACE_BOTTOM: return w.getBlock(wx, wy - 1, wz);
            case Blocks.FACE_NORTH: return w.getBlock(wx, wy, wz + 1);
            case Blocks.FACE_SOUTH: return w.getBlock(wx, wy, wz - 1);
            case Blocks.FACE_EAST: return w.getBlock(wx + 1, wy, wz);
            default: return w.getBlock(wx - 1, wy, wz);
        }
    }
}