package com.voxelcraft.render;

import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;

import com.voxelcraft.game.Blocks;
import com.voxelcraft.game.Chunk;
import com.voxelcraft.game.FloatList;
import com.voxelcraft.game.Hit;
import com.voxelcraft.game.MeshBuilder;
import com.voxelcraft.game.Mob;
import com.voxelcraft.game.Player;
import com.voxelcraft.game.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameRenderer implements android.opengl.GLSurfaceView.Renderer {

    public static final int LOAD_RADIUS = 5;
    public static final int DRAW_RADIUS = 4;
    public static final int REACH = 5;

    private World world;
    private Player player;

    // input state (UI thread -> GL thread)
    private final AtomicInteger breakQueue = new AtomicInteger();
    private final AtomicInteger placeQueue = new AtomicInteger();
    private final AtomicInteger selectedBlock = new AtomicInteger(Blocks.GRASS);
    private volatile boolean jumpHeld = false;
    private volatile float joyX, joyY;
    private final Object lookLock = new Object();
    private float pendingLookX, pendingLookY;

    private float[] proj = new float[16];
    private float[] view = new float[16];
    private float[] viewProj = new float[16];
    private float[] mvp = new float[16];
    private float[] eye = new float[3];
    private float[] dir = new float[3];
    private float[] identity = new float[16];

    private ShaderProgram blockShader;
    private ShaderProgram lineShader;

    private int textureId = 0;
    private int glStamp = 0;
    private int aPos, aUv, aColor, uMvp, uCamPos, uTex, uFogColor, uFogStart, uFogEnd;
    private int lAPos, lUMvp, lUColor;

    private Hit highlight;

    // persistent buffer for hand block
    private int handBufferId = 0;
    private int handGlStamp = -1;
    private float lineWidth = 2f;

    private long lastFrameNs;
    private float trimTimer = 0f;

    private final ArrayList<Chunk> sorted = new ArrayList<>();

    // mobs
    private static final int MAX_MOBS = 10;
    private final ArrayList<Mob> mobs = new ArrayList<>();
    private final Random mobRnd = new Random();
    private float mobTimer = 3f;

    // cached hand cube vertices (change only when selected block changes)
    private int cachedHandBlock = -1;
    private float[] cacheHandVerts = new float[0];

    // ================= public input API (called from UI thread) =================

    public void queueBreak() { breakQueue.incrementAndGet(); }
    public void queuePlace() { placeQueue.incrementAndGet(); }
    public void selectBlock(int block) { selectedBlock.set(block); }
    public void setJumpHeld(boolean held) { jumpHeld = held; }
    public void setJoystick(float x, float y) { joyX = x; joyY = y; }
    public void lookDelta(float dx, float dy) {
        synchronized (lookLock) {
            pendingLookX += dx;
            pendingLookY += dy;
        }
    }

    public World getWorld() { return world; }
    public Player getPlayer() { return player; }

    // ================= GL =================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        glStamp++;
        GLES20.glClearColor(0.49f, 0.79f, 0.94f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        // line width support
        float[] range = new float[2];
        GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, range, 0);
        lineWidth = Math.max(1f, Math.min(2f, range[1]));

        textureId = createTexture();

        blockShader = new ShaderProgram("block", BLOCK_VS, BLOCK_FS);
        aPos = GLES20.glGetAttribLocation(blockShader.program, "aPosition");
        aUv = GLES20.glGetAttribLocation(blockShader.program, "aUV");
        aColor = GLES20.glGetAttribLocation(blockShader.program, "aColor");
        uMvp = GLES20.glGetUniformLocation(blockShader.program, "uMVP");
        uCamPos = GLES20.glGetUniformLocation(blockShader.program, "uCamPos");
        uTex = GLES20.glGetUniformLocation(blockShader.program, "uTexture");
        uFogColor = GLES20.glGetUniformLocation(blockShader.program, "uFogColor");
        uFogStart = GLES20.glGetUniformLocation(blockShader.program, "uFogStart");
        uFogEnd = GLES20.glGetUniformLocation(blockShader.program, "uFogEnd");

        lineShader = new ShaderProgram("line", LINE_VS, LINE_FS);
        lAPos = GLES20.glGetAttribLocation(lineShader.program, "aPosition");
        lUMvp = GLES20.glGetUniformLocation(lineShader.program, "uMVP");
        lUColor = GLES20.glGetUniformLocation(lineShader.program, "uColor");

        Matrix.setIdentityM(identity, 0);

        initWorld();
    }

    private void initWorld() {
        world = new World(1337);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.ensureChunk(dx, dz);
            }
        }
        regenerateMeshes();

        world.spawnX = 8.5;
        world.spawnZ = 8.5;
        world.spawnY = 24;
        world.findSpawn();
        if (world.spawnY - 1 < World.WATER_LEVEL) {
            world.spawnY = World.WATER_LEVEL + 1;
        }

        player = new Player(world.spawnX, world.spawnY, world.spawnZ);
        lastFrameNs = System.nanoTime();
    }

    private void regenerateMeshes() {
        world.rebuildDirtyMeshes();
    }

    private int createTexture() {
        android.graphics.Bitmap bmp = TextureAtlas.generate();
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        bmp.recycle();
        return id;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspect = width / (float) height;
        Matrix.perspectiveM(proj, 0, 70f, aspect, 0.1f, 200f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1000000000f);
        lastFrameNs = now;

        // consume look deltas
        synchronized (lookLock) {
            if (pendingLookX != 0 || pendingLookY != 0) {
                player.look(pendingLookX, pendingLookY, 0.0055f);
                pendingLookX = pendingLookY = 0;
            }
        }

        player.joyX = joyX;
        player.joyY = joyY;
        player.jumpPressed = jumpHeld;

        // world loading
        if (world.isLoadQueueDone()) {
            world.requestAround(player.x, player.z, LOAD_RADIUS);
        }
        world.stepLoading(2);
        world.rebuildDirtyMeshes();

        trimTimer += dt;
        if (trimTimer > 3f) {
            trimTimer = 0f;
            int pcx = (int) Math.floor(player.x / World.CHUNK_SIZE);
            int pcz = (int) Math.floor(player.z / World.CHUNK_SIZE);
            world.trim(pcx, pcz, LOAD_RADIUS + 3);
        }

        // player physics
        player.update(dt, world);

        // mobs
        updateMobs(dt);

        // interactions
        processBreakQueue();
        processPlaceQueue();

        // camera
        player.dir(dir);
        eye[0] = (float) player.eyeX();
        eye[1] = (float) player.eyeY();
        eye[2] = (float) player.eyeZ();
        float cx = eye[0] + dir[0];
        float cy = eye[1] + dir[1];
        float cz = eye[2] + dir[2];
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], cx, cy, cz, 0f, 1f, 0f);
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0);

        highlight = world.raycast(eye[0], eye[1], eye[2], dir[0], dir[1], dir[2], REACH);

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        drawBlockPass();
        drawTransparentPass();
        drawMobs();
        drawHandBlock();
        drawHighlight();
        drawCrosshair();
    }

    // ================= block passes =================

    private void drawBlockPass() {
        blockShader.use();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform3f(uCamPos, eye[0], eye[1], eye[2]);
        GLES20.glUniform3f(uFogColor, 0.49f, 0.79f, 0.94f);
        GLES20.glUniform1f(uFogStart, 34f);
        GLES20.glUniform1f(uFogEnd, 62f);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);

        for (Chunk c : world.getChunks().values()) {
            if (!inDrawRange(c)) continue;
            if (c.solidVerts.length == 0) continue;
            drawChunk(c, false);
        }
    }

    private void drawTransparentPass() {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);

        blockShader.use();
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform3f(uCamPos, eye[0], eye[1], eye[2]);

        // crude distance sort at chunk level
        sorted.clear();
        for (Chunk c : world.getChunks().values()) {
            if (!inDrawRange(c)) continue;
            if (c.transparentVerts.length == 0) continue;
            sorted.add(c);
        }
        final float ex = (float) player.x, ez = (float) player.z;
        sorted.sort((a, b) -> {
            float da = chunkDist(a, ex, ez);
            float db = chunkDist(b, ex, ez);
            return Float.compare(db, da); // far first
        });

        for (Chunk c : sorted) {
            drawChunk(c, true);
        }
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private boolean inDrawRange(Chunk c) {
        int pcx = (int) Math.floor(player.x / World.CHUNK_SIZE);
        int pcz = (int) Math.floor(player.z / World.CHUNK_SIZE);
        int dx = Math.abs(c.cx - pcx);
        int dz = Math.abs(c.cz - pcz);
        return Math.max(dx, dz) <= DRAW_RADIUS;
    }

    private float chunkDist(Chunk c, float ex, float ez) {
        float x = c.cx * World.CHUNK_SIZE + 8 - ex;
        float z = c.cz * World.CHUNK_SIZE + 8 - ez;
        return x * x + z * z;
    }

    private void drawChunk(Chunk c, boolean transparent) {
        ensureGlBuffers(c);
        int bufId = transparent ? c.transparentBufferId : c.solidBufferId;
        int floatCount = (transparent ? c.transparentVerts : c.solidVerts).length;
        if (bufId == 0 || floatCount == 0) return;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufId);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 9 * 4, 3 * 4);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 9 * 4, 5 * 4);

        GLES20.glUniformMatrix4fv(uMvp, 1, false, viewProj, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, floatCount / MeshBuilder.FLOATS_PER_VERTEX);
    }

    private void ensureGlBuffers(Chunk c) {
        if (c.glStamp == glStamp) return;
        c.solidBufferId = allocBuffer(c.solidVerts);
        c.transparentBufferId = allocBuffer(c.transparentVerts);
        c.glStamp = glStamp;
    }

    private int allocBuffer(float[] verts) {
        if (verts.length == 0) return 0;
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0]);
        ByteBuffer bb = ByteBuffer.allocateDirect(verts.length * 4).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(verts);
        bb.position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bb.capacity(), bb, GLES20.GL_STATIC_DRAW);
        return ids[0];
    }

    // ================= hand block =================

    private void drawHandBlock() {
        if (player == null) return;
        int block = selectedBlock.get();
        ensureHandVerts(block);
        if (cacheHandVerts.length == 0) return;
        if (handGlStamp != glStamp || handBufferId == 0) {
            handBufferId = allocBuffer(cacheHandVerts);
            handGlStamp = glStamp;
        }

        float r = (float) Math.cos(player.yaw);
        float rz = (float) -Math.sin(player.yaw);
        float hx = eye[0] + dir[0] * 1.7f - r * 0.62f;
        float hy = eye[1] + dir[1] * 1.7f - 0.55f;
        float hz = eye[2] + dir[2] * 1.7f - rz * 0.62f;

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        blockShader.use();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform3f(uCamPos, eye[0], eye[1], eye[2]);
        GLES20.glUniform3f(uFogColor, 0.49f, 0.79f, 0.94f);
        GLES20.glUniform1f(uFogStart, 10f);
        GLES20.glUniform1f(uFogEnd, 50f);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, handBufferId);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 9 * 4, 0);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 9 * 4, 3 * 4);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 9 * 4, 5 * 4);

        float size = 0.28f;
        Matrix.setIdentityM(mvp, 0);
        Matrix.translateM(mvp, 0, hx, hy, hz);
        Matrix.scaleM(mvp, 0, size, size, size);
        Matrix.multiplyMM(mvp, 0, viewProj, 0, mvp, 0);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, cacheHandVerts.length / MeshBuilder.FLOATS_PER_VERTEX);

        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void ensureHandVerts(int block) {
        if (block == cachedHandBlock) return;
        cachedHandBlock = block;
        FloatList list = new FloatList(1024);
        for (int f = 0; f < 6; f++) {
            MeshBuilder.addCubeFace(list, 0, 0, 0, f, (byte) block);
        }
        cacheHandVerts = list.toArray();
    }

    // ================= highlight + crosshair =================

    private void drawHighlight() {
        if (highlight == null) return;
        lineShader.use();
        GLES20.glUniformMatrix4fv(lUMvp, 1, false, viewProj, 0);
        GLES20.glUniform4f(lUColor, 0f, 0f, 0f, 0.9f);
        GLES20.glLineWidth(lineWidth);
        drawBox(hitEdges(highlight));
    }

    private float[] hitEdges(Hit h) {
        float x = h.x, y = h.y, z = h.z;
        return new float[]{
                x, y, z, x + 1, y, z,
                x + 1, y, z, x + 1, y, z + 1,
                x + 1, y, z + 1, x, y, z + 1,
                x, y, z + 1, x, y, z,

                x, y + 1, z, x + 1, y + 1, z,
                x + 1, y + 1, z, x + 1, y + 1, z + 1,
                x + 1, y + 1, z + 1, x, y + 1, z + 1,
                x, y + 1, z + 1, x, y + 1, z,

                x, y, z, x, y + 1, z,
                x + 1, y, z, x + 1, y + 1, z,
                x + 1, y, z + 1, x + 1, y + 1, z + 1,
                x, y, z + 1, x, y + 1, z + 1,
        };
    }

    private void drawBox(float[] pts) {
        if (pts.length == 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ByteBuffer bb = ByteBuffer.allocateDirect(pts.length * 4).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(pts);
        bb.position(0);
        GLES20.glEnableVertexAttribArray(lAPos);
        GLES20.glVertexAttribPointer(lAPos, 3, GLES20.GL_FLOAT, false, 12, bb);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, pts.length / 3);
    }

    private void drawCrosshair() {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        lineShader.use();
        GLES20.glUniformMatrix4fv(lUMvp, 1, false, identity, 0);
        GLES20.glUniform4f(lUColor, 1f, 1f, 1f, 0.75f);
        GLES20.glLineWidth(lineWidth);
        float[] pts = {
                -0.018f, 0f, 0f, 0.018f, 0f, 0f,
                0f, -0.018f, 0f, 0f, 0.018f, 0f,
        };
        ByteBuffer bb = ByteBuffer.allocateDirect(pts.length * 4).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(pts);
        bb.position(0);
        GLES20.glEnableVertexAttribArray(lAPos);
        GLES20.glVertexAttribPointer(lAPos, 3, GLES20.GL_FLOAT, false, 12, bb);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 4);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    // ================= mobs =================

    private void updateMobs(float dt) {
        mobTimer -= dt;
        if (mobTimer <= 0 && mobs.size() < MAX_MOBS) {
            mobTimer = 4f + mobRnd.nextFloat() * 4f;
            trySpawnMob();
            if (mobs.size() < MAX_MOBS && mobRnd.nextFloat() < 0.5f) trySpawnMob();
        }
        double px = player.x, pz = player.z;
        for (int i = mobs.size() - 1; i >= 0; i--) {
            Mob m = mobs.get(i);
            m.update(dt, world);
            if (Math.abs(m.x - px) > 44 || Math.abs(m.z - pz) > 44 || m.y < -6) {
                mobs.remove(i);
            }
        }
    }

    private void trySpawnMob() {
        for (int attempt = 0; attempt < 8; attempt++) {
            double ang = mobRnd.nextDouble() * 6.2831853;
            double dist = 12 + mobRnd.nextDouble() * 16;
            int wx = (int) Math.floor(player.x + Math.sin(ang) * dist);
            int wz = (int) Math.floor(player.z + Math.cos(ang) * dist);
            int cx = (int) Math.floorDiv(wx, World.CHUNK_SIZE);
            int cz = (int) Math.floorDiv(wz, World.CHUNK_SIZE);
            if (world.getChunk(cx, cz) == null) continue;
            int top = world.topSolid(wx, wz);
            if (top < World.WATER_LEVEL - 1 || top > 40) continue;
            int seed = mobRnd.nextInt();
            mobs.add(new Mob(wx + 0.5, top + 1, wz + 0.5, seed));
            return;
        }
    }

    private void drawMobs() {
        if (mobs.isEmpty()) return;
        FloatList ml = new FloatList(mobs.size() * 512);
        for (Mob m : mobs) {
            buildMobMesh(ml, m);
        }
        if (ml.size() == 0) return;

        blockShader.use();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform3f(uCamPos, eye[0], eye[1], eye[2]);
        GLES20.glUniform3f(uFogColor, 0.49f, 0.79f, 0.94f);
        GLES20.glUniform1f(uFogStart, 20f);
        GLES20.glUniform1f(uFogEnd, 55f);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);

        ByteBuffer bb = ByteBuffer.allocateDirect(ml.size() * 4).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(ml.toArray());
        bb.position(0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 9 * 4, bb);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 9 * 4, 3 * 4);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 9 * 4, 5 * 4);

        GLES20.glUniformMatrix4fv(uMvp, 1, false, viewProj, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, ml.size() / MeshBuilder.FLOATS_PER_VERTEX);
    }

    private void buildMobMesh(FloatList out, Mob m) {
        float r, g, b;
        switch (m.kind) {
            case PIG: r = 0.95f; g = 0.71f; b = 0.74f; break;
            case COW: r = 0.88f; g = 0.78f; b = 0.62f; break;
            default: r = 0.93f; g = 0.93f; b = 0.92f; break; // SHEEP
        }
        float fx = (float) m.x, fy = (float) m.y, fz = (float) m.z;

        // legs
        float leg = 0.26f;
        for (int i = 0; i < 4; i++) {
            float lx = (i & 1) == 0 ? fx - 0.18f : fx + 0.18f;
            float lz = (i & 2) == 0 ? fz - 0.22f : fz + 0.22f;
            MeshBuilder.addColoredCube(out, lx, fy + 0.15, lz, leg, r * 0.62f, g * 0.62f, b * 0.62f);
        }
        // body
        MeshBuilder.addColoredCube(out, fx, fy + 0.55, fz, 0.62, r, g, b);
        // head
        MeshBuilder.addColoredCube(out, fx, fy + 0.78, fz, 0.55, r * 0.85f, g * 0.85f, b * 0.85f);
    }

    // ================= interactions =================

    private void processBreakQueue() {
        int n = breakQueue.getAndSet(0);
        while (n-- > 0) {
            Hit hit = target();
            if (hit == null) continue;
            byte b = world.getBlock(hit.x, hit.y, hit.z);
            if (b == Blocks.BEDROCK) continue;
            if (hit.y < 1) continue;
            world.setBlock(hit.x, hit.y, hit.z, Blocks.AIR);
        }
    }

    private void processPlaceQueue() {
        int n = placeQueue.getAndSet(0);
        while (n-- > 0) {
            Hit hit = target();
            if (hit == null) continue;
            int px = hit.placeX(), py = hit.placeY(), pz = hit.placeZ();
            if (py < 1 || py >= Chunk.SY) continue;
            byte cur = world.getBlock(px, py, pz);
            if (cur != Blocks.AIR && !Blocks.isWater(cur)) continue;
            // make sure the chunk exists before placing
            int cx = (int) Math.floorDiv(px, World.CHUNK_SIZE);
            int cz = (int) Math.floorDiv(pz, World.CHUNK_SIZE);
            if (world.getChunk(cx, cz) == null) {
                int dx = Math.abs(cx - (int) Math.floor(player.x / World.CHUNK_SIZE));
                int dz = Math.abs(cz - (int) Math.floor(player.z / World.CHUNK_SIZE));
                if (Math.max(dx, dz) > LOAD_RADIUS + 1) continue;
                world.ensureChunk(cx, cz);
            }
            world.setBlock(px, py, pz, (byte) selectedBlock.get());
        }
    }

    private Hit target() {
        player.dir(dir);
        return world.raycast(eye[0], eye[1], eye[2], dir[0], dir[1], dir[2], REACH);
    }

    private static final String BLOCK_VS =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aUV;\n" +
            "attribute vec4 aColor;\n" +
            "uniform mat4 uMVP;\n" +
            "uniform vec3 uCamPos;\n" +
            "varying vec2 vUV;\n" +
            "varying vec4 vColor;\n" +
            "varying float vDist;\n" +
            "void main() {\n" +
            "  gl_Position = uMVP * aPosition;\n" +
            "  vDist = distance(uCamPos, aPosition.xyz);\n" +
            "  vUV = aUV;\n" +
            "  vColor = aColor;\n" +
            "}\n";

    private static final String BLOCK_FS =
            "precision mediump float;\n" +
            "varying vec2 vUV;\n" +
            "varying vec4 vColor;\n" +
            "varying float vDist;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogStart;\n" +
            "uniform float uFogEnd;\n" +
            "void main() {\n" +
            "  vec4 tex = texture2D(uTexture, vUV);\n" +
            "  if (tex.a < 0.10) discard;\n" +
            "  vec4 c = vec4(tex.rgb * vColor.rgb, tex.a * vColor.a);\n" +
            "  float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);\n" +
            "  c.rgb = mix(c.rgb, uFogColor, fog);\n" +
            "  gl_FragColor = c;\n" +
            "}\n";

    private static final String LINE_VS =
            "attribute vec4 aPosition;\n" +
            "uniform mat4 uMVP;\n" +
            "void main() {\n" +
            "  gl_Position = uMVP * aPosition;\n" +
            "}\n";

    private static final String LINE_FS =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main() {\n" +
            "  gl_FragColor = uColor;\n" +
            "}\n";
}