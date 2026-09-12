package com.voxelcraft.render;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.voxelcraft.game.Blocks;

import java.util.Random;

/**
 * Procedurally generated 4x4 atlas of 16×16 block textures.
 * Total bitmap 64×64 ARGB_8888.
 */
public final class TextureAtlas {

    public static final int TILE = 16;
    public static final int SIZE = TILE * Blocks.TILES_PER_ROW; // 64

    private TextureAtlas() {
    }

    public static Bitmap generate() {
        int size = SIZE;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Random rnd = new Random(1337);

        fillTile(bmp, rnd, 0, 0, 0x5E9C32, 0x4E8826);  // grass top
        fillGrassSide(bmp, rnd);
        fillTile(bmp, rnd, 2, 0, 0x8A5A2B, 0x6B4522);  // dirt
        fillTile(bmp, rnd, 3, 0, 0x808080, 0x606060);  // stone
        fillTile(bmp, rnd, 0, 1, 0xE7D8A0, 0xCFC08A);  // sand
        fillTileAlpha(bmp, rnd, 1, 1, 0x3873C8, 0x28589E, 180); // water
        fillWoodSide(bmp, rnd);
        fillTile(bmp, rnd, 3, 1, 0x9B7330, 0x7A5C24);  // wood top
        fillLeaves(bmp, rnd);
        fillTile(bmp, rnd, 1, 2, 0xC4A055, 0x9B8044);  // planks
        fillTile(bmp, rnd, 3, 2, 0xFFFFFF, 0xF2F2F2);  // white (mobs)
        return bmp;
    }

    /** Generic noise-filled tile. */
    private static void fillTile(Bitmap bmp, Random rnd, int tx, int ty, int c1, int c2) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float f = rnd.nextFloat();
                int col = lerpColor(c1, c2, f);
                bmp.setPixel(tx * TILE + x, ty * TILE + y, col);
            }
        }
    }

    private static void fillTileAlpha(Bitmap bmp, Random rnd, int tx, int ty, int c1, int c2, int alpha) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float f = rnd.nextFloat();
                int rgb = lerpColor(c1, c2, f);
                bmp.setPixel(tx * TILE + x, ty * TILE + y,
                        Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb)));
            }
        }
    }

    /** Grass side: dirt body, green strip top 3-4 px, occasional green drip. */
    private static void fillGrassSide(Bitmap bmp, Random rnd) {
        int tx = 1, ty = 0;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int rgb;
                if (y < 3) {
                    float f = rnd.nextFloat();
                    rgb = lerpColor(0x5E9C32, 0x4E8826, f);
                } else if (y < 5) {
                    float f = rnd.nextFloat();
                    rgb = (f < 0.45f) ? lerpColor(0x5E9C32, 0x4E8826, f) : lerpColor(0x8A5A2B, 0x6B4522, f);
                } else {
                    float f = rnd.nextFloat();
                    rgb = lerpColor(0x8A5A2B, 0x6B4522, f);
                }
                bmp.setPixel(tx * TILE + x, ty * TILE + y, rgb);
            }
        }
    }

    private static void fillWoodSide(Bitmap bmp, Random rnd) {
        int tx = 2, ty = 1;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                boolean stripe = (x % 4 == 0 || x % 4 == 3);
                float f = rnd.nextFloat();
                int rgb = stripe ? lerpColor(0x5A391C, 0x4A2E14, f) : lerpColor(0x6B4522, 0x5A391C, f);
                bmp.setPixel(tx * TILE + x, ty * TILE + y, rgb);
            }
        }
    }

    private static void fillLeaves(Bitmap bmp, Random rnd) {
        int tx = 0, ty = 2;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                float f = rnd.nextFloat();
                int rgb = lerpColor(0x3E9B1F, 0x1F730D, f);
                bmp.setPixel(tx * TILE + x, ty * TILE + y, rgb);
            }
        }
    }

    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) (Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = (int) (Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = (int) (Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.rgb(r, g, b);
    }
}