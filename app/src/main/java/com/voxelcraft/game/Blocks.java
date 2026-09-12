package com.voxelcraft.game;

/**
 * Block type registry and tile atlas mapping.
 */
public final class Blocks {

    public static final byte AIR = 0;
    public static final byte GRASS = 1;
    public static final byte DIRT = 2;
    public static final byte STONE = 3;
    public static final byte SAND = 4;
    public static final byte WATER = 5;
    public static final byte WOOD = 6;
    public static final byte LEAVES = 7;
    public static final byte BEDROCK = 8;
    public static final byte PLANKS = 9;

    // face indices
    public static final int FACE_TOP = 0;
    public static final int FACE_BOTTOM = 1;
    public static final int FACE_NORTH = 2; // +Z
    public static final int FACE_SOUTH = 3; // -Z
    public static final int FACE_EAST = 4;  // +X
    public static final int FACE_WEST = 5;  // -X

    public static final int TILES_PER_ROW = 4;
    public static final int[] TILE_GRASS_TOP = {0, 0};
    public static final int[] TILE_GRASS_SIDE = {1, 0};
    public static final int[] TILE_DIRT = {2, 0};
    public static final int[] TILE_STONE = {3, 0};
    public static final int[] TILE_SAND = {0, 1};
    public static final int[] TILE_WATER = {1, 1};
    public static final int[] TILE_WOOD_SIDE = {2, 1};
    public static final int[] TILE_WOOD_TOP = {3, 1};
    public static final int[] TILE_LEAVES = {0, 2};
    public static final int[] TILE_PLANKS = {1, 2};
    public static final int[] TILE_WHITE = {3, 2};

    private Blocks() {
    }

    public static boolean isSolid(byte b) {
        return b != AIR && b != WATER;
    }

    /** blocks that fully hide faces behind them */
    public static boolean isOpaque(byte b) {
        return b != AIR && b != WATER;
    }

    public static boolean isWater(byte b) {
        return b == WATER;
    }

    public static boolean isAir(byte b) {
        return b == AIR;
    }

    /**
     * Whether the face of "block" facing "neighbor" should be drawn.
     */
    public static boolean faceVisible(byte block, byte neighbor) {
        if (block == WATER) {
            return neighbor != WATER;
        }
        return !isOpaque(neighbor);
    }

    public static int[] tileFor(byte block, int face) {
        switch (block) {
            case GRASS:
                if (face == FACE_TOP) return TILE_GRASS_TOP;
                if (face == FACE_BOTTOM) return TILE_DIRT;
                return TILE_GRASS_SIDE;
            case DIRT:
                return TILE_DIRT;
            case STONE:
                return TILE_STONE;
            case SAND:
                return TILE_SAND;
            case WATER:
                return TILE_WATER;
            case WOOD:
                if (face == FACE_TOP || face == FACE_BOTTOM) return TILE_WOOD_TOP;
                return TILE_WOOD_SIDE;
            case LEAVES:
                return TILE_LEAVES;
            case PLANKS:
                return TILE_PLANKS;
            case BEDROCK:
                return TILE_STONE;
            default:
                return TILE_DIRT;
        }
    }

    /** base color tint per block (multiplied with per-face shade) */
    public static float[] baseColor(byte block) {
        switch (block) {
            case GRASS: return new float[]{1f, 1f, 1f};
            case WATER: return new float[]{0.62f, 0.80f, 1f};
            case LEAVES: return new float[]{1f, 1f, 1f};
            default: return new float[]{1f, 1f, 1f};
        }
    }

    public static float alpha(byte block) {
        return block == WATER ? 0.85f : 1f;
    }

    /** how strongly a given face is lit (fake directional light) */
    public static float faceShade(int face) {
        switch (face) {
            case FACE_TOP: return 1.00f;
            case FACE_NORTH:
            case FACE_SOUTH: return 0.82f;
            case FACE_EAST:
            case FACE_WEST: return 0.66f;
            case FACE_BOTTOM: return 0.50f;
            default: return 0.8f;
        }
    }
}