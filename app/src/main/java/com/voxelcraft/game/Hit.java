package com.voxelcraft.game;

/**
 * Result of a raycast through the voxel grid.
 */
public final class Hit {
    public final int x, y, z; // block that was hit
    public final int nx, ny, nz; // face normal of the hit

    public Hit(int x, int y, int z, int nx, int ny, int nz) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public int placeX() { return x + nx; }
    public int placeY() { return y + ny; }
    public int placeZ() { return z + nz; }
}