package com.voxelcraft.game;

public final class Player {

    public double x, y, z;      // feet position
    public double vx, vy, vz;
    public float yaw, pitch;    // radians
    public float joyX, joyY;    // -1..1
    public boolean jumpPressed;
    public boolean onGround;
    public boolean inWater;

    private static final double HALF = 0.3;
    private static final double HEIGHT = 1.8;
    private static final float WALK = 4.6f;
    private static final float GRAVITY = 26f;
    private static final float JUMP = 8.6f;

    public Player(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = (float) Math.PI; // face -Z by default
        this.pitch = 0;
    }

    public void reset(double px, double py, double pz) {
        x = px; y = py; z = pz;
        vx = vy = vz = 0;
        onGround = false;
        inWater = false;
    }

    public double eyeX() { return x; }
    public double eyeY() { return y + 1.62; }
    public double eyeZ() { return z; }

    public void dir(float[] out) {
        float cp = (float) Math.cos(pitch);
        out[0] = (float) (-Math.sin(yaw) * cp);
        out[1] = (float) Math.sin(pitch);
        out[2] = (float) (-Math.cos(yaw) * cp);
    }

    public void look(float dx, float dy, float sensitivity) {
        yaw -= dx * sensitivity;
        pitch -= dy * sensitivity;
        if (pitch > 1.55f) pitch = 1.55f;
        if (pitch < -1.55f) pitch = -1.55f;
    }

    public void update(float dt, World w) {
        float fx = (float) -Math.sin(yaw);
        float fz = (float) -Math.cos(yaw);
        float rx = (float) Math.cos(yaw);
        float rz = (float) -Math.sin(yaw);

        double moveX = fx * joyY + rx * joyX;
        double moveZ = fz * joyY + rz * joyX;
        double mag = Math.hypot(moveX, moveZ);
        if (mag > 0) {
            moveX /= mag;
            moveZ /= mag;
        }
        double speed = inWater ? WALK * 0.55 : WALK;
        vx = moveX * speed;
        vz = moveZ * speed;

        vy -= GRAVITY * dt;
        if (vy < -50) vy = -50;

        if (jumpPressed) {
            if (inWater) {
                vy += 30 * dt;
                if (vy > 4.5) vy = 4.5;
            } else if (onGround) {
                vy = JUMP;
                onGround = false;
            }
        }

        if (inWater) {
            vy += 20 * dt;
            if (vy > 4.5) vy = 4.5;
        }

        moveY(dt, w);
        moveX(dt, w);
        moveZ(dt, w);

        updateWater(w);

        if (y < -40) {
            y = w.spawnY;
            x = w.spawnX;
            z = w.spawnZ;
            vx = vy = vz = 0;
            onGround = false;
        }
    }

    private void moveX(float dt, World w) {
        x += vx * dt;
        int x0 = (int) Math.floor(x - HALF);
        int x1 = (int) Math.floor(x + HALF);
        int y0 = (int) Math.floor(y + 0.01);
        int y1 = (int) Math.floor(y + HEIGHT - 0.01);
        int z0 = (int) Math.floor(z - HALF + 0.01);
        int z1 = (int) Math.floor(z + HALF - 0.01);
        if (vx < 0) {
            for (int bx = x0; bx <= x1; bx++) {
                if (anySolid(bx, y0, y1, z0, z1, w)) {
                    x = bx + 1 + HALF;
                    vx = 0;
                    break;
                }
            }
        } else if (vx > 0) {
            for (int bx = x1; bx >= x0; bx--) {
                if (anySolid(bx, y0, y1, z0, z1, w)) {
                    x = bx - HALF;
                    vx = 0;
                    break;
                }
            }
        }
    }

    private void moveZ(float dt, World w) {
        z += vz * dt;
        int z0 = (int) Math.floor(z - HALF);
        int z1 = (int) Math.floor(z + HALF);
        int y0 = (int) Math.floor(y + 0.01);
        int y1 = (int) Math.floor(y + HEIGHT - 0.01);
        int x0 = (int) Math.floor(x - HALF + 0.01);
        int x1 = (int) Math.floor(x + HALF - 0.01);
        if (vz < 0) {
            for (int bz = z0; bz <= z1; bz++) {
                if (anySolidVert(x0, x1, y0, y1, bz, w)) {
                    z = bz + 1 + HALF;
                    vz = 0;
                    break;
                }
            }
        } else if (vz > 0) {
            for (int bz = z1; bz >= z0; bz--) {
                if (anySolidVert(x0, x1, y0, y1, bz, w)) {
                    z = bz - HALF;
                    vz = 0;
                    break;
                }
            }
        }
    }

    private void moveY(float dt, World w) {
        y += vy * dt;
        int x0 = (int) Math.floor(x - HALF);
        int x1 = (int) Math.floor(x + HALF);
        int z0 = (int) Math.floor(z - HALF);
        int z1 = (int) Math.floor(z + HALF);

        if (vy <= 0) {
            int by = (int) Math.floor(y - 0.0001);
            boolean grounded = anySolid2D(x0, x1, by, z0, z1, w);
            if (grounded) {
                y = by + 1;
                vy = 0;
            }
            onGround = grounded;
        } else {
            int ty = (int) Math.floor(y + HEIGHT);
            if (anySolid2D(x0, x1, ty, z0, z1, w)) {
                y = ty - HEIGHT;
                vy = 0;
            }
        }
    }

    private boolean anySolid(int bx, int by0, int by1, int bz0, int bz1, World w) {
        for (int by = by0; by <= by1; by++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                if (solid(bx, by, bz, w)) return true;
            }
        }
        return false;
    }

    private boolean anySolidVert(int bx0, int bx1, int by0, int by1, int bz, World w) {
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int by = by0; by <= by1; by++) {
                if (solid(bx, by, bz, w)) return true;
            }
        }
        return false;
    }

    private boolean anySolid2D(int bx0, int bx1, int by, int bz0, int bz1, World w) {
        for (int bx = bx0; bx <= bx1; bx++) {
            for (int bz = bz0; bz <= bz1; bz++) {
                if (solid(bx, by, bz, w)) return true;
            }
        }
        return false;
    }

    private boolean solid(int x, int y, int z, World w) {
        return Blocks.isSolid(w.getBlock(x, y, z));
    }

    private void updateWater(World w) {
        inWater = Blocks.isWater(w.getBlock((int) Math.floor(x), (int) Math.floor(y + 0.6), (int) Math.floor(z)))
                || Blocks.isWater(w.getBlock((int) Math.floor(x), (int) Math.floor(y + 1.2), (int) Math.floor(z)));
    }

    public void respawn(World w) {
        reset(w.spawnX, w.spawnY, w.spawnZ);
    }
}