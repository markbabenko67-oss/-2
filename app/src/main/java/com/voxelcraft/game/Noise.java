package com.voxelcraft.game;

/**
 * Deterministic value noise (Perlin-style) used for terrain generation.
 * All functions are pure functions of coordinates + a fixed seed, so chunks
 * always generate identically regardless of load order.
 */
public final class Noise {

    private final int seed;

    public Noise(int seed) {
        this.seed = seed;
    }

    private int hash(int x, int y) {
        int n = x * 374761393 + y * 668265263 + seed * 179424673;
        n = (n ^ (n >>> 13)) * 1274126177;
        n = n ^ (n >>> 16);
        return n;
    }

    private int hash(int x, int y, int z) {
        int n = x * 374761393 + y * 668265263 + z * 2147483647 + seed * 179424673;
        n = (n ^ (n >>> 13)) * 1274126177;
        n = n ^ (n >>> 16);
        return n;
    }

    /** int hash for tree placement decisions */
    public int hashInt(int x, int z) {
        return hash(x, z);
    }

    public float hash01(int x, int y) {
        return (hash(x, y) & 0x7fffffff) / 2147483647f;
    }

    public float hash01(int x, int y, int z) {
        return (hash(x, y, z) & 0x7fffffff) / 2147483647f;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    public float noise2D(float x, float z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        float xf = x - xi;
        float zf = z - zi;
        float u = smooth(xf);
        float v = smooth(zf);
        float v00 = hash01(xi, zi);
        float v10 = hash01(xi + 1, zi);
        float v01 = hash01(xi, zi + 1);
        float v11 = hash01(xi + 1, zi + 1);
        return lerp(lerp(v00, v10, u), lerp(v01, v11, u), v);
    }

    public float noise3D(float x, float y, float z) {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;
        float u = smooth(xf);
        float v = smooth(yf);
        float w = smooth(zf);
        float c000 = hash01(xi, yi, zi);
        float c100 = hash01(xi + 1, yi, zi);
        float c010 = hash01(xi, yi + 1, zi);
        float c110 = hash01(xi + 1, yi + 1, zi);
        float c001 = hash01(xi, yi, zi + 1);
        float c101 = hash01(xi + 1, yi, zi + 1);
        float c011 = hash01(xi, yi + 1, zi + 1);
        float c111 = hash01(xi + 1, yi + 1, zi + 1);
        float x00 = lerp(c000, c100, u);
        float x10 = lerp(c010, c110, u);
        float x01 = lerp(c001, c101, u);
        float x11 = lerp(c011, c111, u);
        float y0 = lerp(x00, x10, v);
        float y1 = lerp(x01, x11, v);
        return lerp(y0, y1, w);
    }

    public float fbm2D(float x, float z, int octaves, float lacunarity, float gain) {
        float amp = 1f;
        float freq = 1f;
        float sum = 0f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise2D(x * freq, z * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    public float fbm3D(float x, float y, float z, int octaves, float lacunarity, float gain) {
        float amp = 1f;
        float freq = 1f;
        float sum = 0f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise3D(x * freq, y * freq, z * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}