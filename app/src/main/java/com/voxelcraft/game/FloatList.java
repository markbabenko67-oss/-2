package com.voxelcraft.game;

import java.util.Arrays;

/** Minimal growable float array. */
public final class FloatList {
    private float[] a;
    private int n;

    public FloatList() {
        this(1024);
    }

    public FloatList(int capacity) {
        a = new float[Math.max(16, capacity)];
    }

    public void add(float v) {
        if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
        a[n++] = v;
    }

    public int size() {
        return n;
    }

    public float[] toArray() {
        return Arrays.copyOf(a, n);
    }
}