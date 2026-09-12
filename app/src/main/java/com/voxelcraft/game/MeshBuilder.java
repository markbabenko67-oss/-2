package com.voxelcraft.game;

/**
 * Builds vertex data for a cube face.
 * Vertex layout: pos(3) + uv(2) + color(4) = 9 floats.
 */
public final class MeshBuilder {

    public static final int FLOATS_PER_VERTEX = 9;
    public static final int FLOATS_PER_FACE = FLOATS_PER_VERTEX * 4;

    private MeshBuilder() {
    }

    /**
     * Appends one axis-aligned face of the unit cube at (x,y,z).
     * face is one of Blocks.FACE_*.
     */
    public static void addCubeFace(FloatList out, int x, int y, int z, int face, byte block) {
        int[] tile = Blocks.tileFor(block, face);
        float shade = Blocks.faceShade(face);
        float alpha = Blocks.alpha(block);
        float[] tint = Blocks.baseColor(block);

        float tileSize = 1f / Blocks.TILES_PER_ROW;
        float inset = 0.5f / (Blocks.TILES_PER_ROW * 16f);
        float u0 = tile[0] * tileSize + inset;
        float u1 = (tile[0] + 1) * tileSize - inset;
        float v0 = 1f - (tile[1] + 1) * tileSize + inset;
        float v1 = 1f - tile[1] * tileSize - inset;

        float fx = x, fy = y, fz = z;

        float ox, oy, oz, uax, uay, uaz, vax, vay, vaz;
        switch (face) {
            case Blocks.FACE_TOP:
                ox = fx; oy = fy + 1; oz = fz;
                uax = 1; uay = 0; uaz = 0;
                vax = 0; vay = 0; vaz = 1;
                break;
            case Blocks.FACE_BOTTOM:
                ox = fx; oy = fy; oz = fz;
                uax = 1; uay = 0; uaz = 0;
                vax = 0; vay = 0; vaz = 1;
                break;
            case Blocks.FACE_NORTH: // +Z
                ox = fx; oy = fy; oz = fz + 1;
                uax = 1; uay = 0; uaz = 0;
                vax = 0; vay = 1; vaz = 0;
                break;
            case Blocks.FACE_SOUTH: // -Z
                ox = fx; oy = fy; oz = fz;
                uax = 1; uay = 0; uaz = 0;
                vax = 0; vay = 1; vaz = 0;
                break;
            case Blocks.FACE_EAST: // +X
                ox = fx + 1; oy = fy; oz = fz;
                uax = 0; uay = 0; uaz = 1;
                vax = 0; vay = 1; vaz = 0;
                break;
            default: // FACE_WEST -X
                ox = fx; oy = fy; oz = fz;
                uax = 0; uay = 0; uaz = 1;
                vax = 0; vay = 1; vaz = 0;
                break;
        }

        float r = tint[0] * shade;
        float g = tint[1] * shade;
        float b = tint[2] * shade;

        emitFace(out, ox, oy, oz, uax, uay, uaz, vax, vay, vaz,
                u0, u1, v0, v1, r, g, b, alpha);
    }

    /**
     * Adds a full opaque cube centered at (cx,cy,cz) with the given half-size,
     * tinted with a solid color (uses the white atlas tile). Used for mobs.
     */
    public static void addColoredCube(FloatList out, double cx, double cy, double cz,
                                      double size, float r, float g, float b) {
        float s = (float) (size * 0.5);
        int[] white = Blocks.TILE_WHITE;
        float tileSize = 1f / Blocks.TILES_PER_ROW;
        float u0 = white[0] * tileSize;
        float u1 = (white[0] + 1) * tileSize;
        float v0 = 1f - (white[1] + 1) * tileSize;
        float v1 = 1f - white[1] * tileSize;

        float x0 = (float) cx - s, x1 = (float) cx + s;
        float y0 = (float) cy - s, y1 = (float) cy + s;
        float z0 = (float) cz - s, z1 = (float) cz + s;
        float two = 2 * s;

        emitFace(out, x0, y1, z0, two, 0, 0, 0, 0, two, u0, u1, v0, v1, r, g, b, 1f); // top
        emitFace(out, x0, y0, z0, two, 0, 0, 0, 0, two, u0, u1, v0, v1, r * 0.5f, g * 0.5f, b * 0.5f, 1f); // bottom
        emitFace(out, x0, y0, z1, two, 0, 0, 0, two, 0, u0, u1, v0, v1, r * 0.82f, g * 0.82f, b * 0.82f, 1f); // +z
        emitFace(out, x0, y0, z0, two, 0, 0, 0, two, 0, u0, u1, v0, v1, r * 0.82f, g * 0.82f, b * 0.82f, 1f); // -z
        emitFace(out, x1, y0, z0, 0, 0, two, 0, two, 0, u0, u1, v0, v1, r * 0.66f, g * 0.66f, b * 0.66f, 1f); // +x
        emitFace(out, x0, y0, z0, 0, 0, two, 0, two, 0, u0, u1, v0, v1, r * 0.66f, g * 0.66f, b * 0.66f, 1f); // -x
    }

    private static void emitFace(FloatList out,
                                 float ox, float oy, float oz,
                                 float uax, float uay, float uaz,
                                 float vax, float vay, float vaz,
                                 float u0, float u1, float v0, float v1,
                                 float r, float g, float b, float a) {
        for (int i = 0; i < 4; i++) {
            float px = ox + ((i & 1) == 0 ? 0 : uax) + ((i >> 1) == 0 ? 0 : vax);
            float py = oy + ((i & 1) == 0 ? 0 : uay) + ((i >> 1) == 0 ? 0 : vay);
            float pz = oz + ((i & 1) == 0 ? 0 : uaz) + ((i >> 1) == 0 ? 0 : vaz);
            float u = ((i & 1) == 0) ? u0 : u1;
            float v = ((i >> 1) == 0) ? v0 : v1;
            out.add(px);
            out.add(py);
            out.add(pz);
            out.add(u);
            out.add(v);
            out.add(r);
            out.add(g);
            out.add(b);
            out.add(a);
        }
    }

    /** Two triangles sharing (p0,p1,p2) and (p0,p2,p3). */
    public static int indexOf(int base, int corner) {
        return base + corner;
    }
}