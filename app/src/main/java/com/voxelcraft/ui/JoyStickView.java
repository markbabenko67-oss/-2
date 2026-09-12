package com.voxelcraft.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import com.voxelcraft.R;

public final class JoyStickView extends View {

    private final float density;
    private final float maxRadius;
    private final Drawable base;
    private final Drawable nub;
    private float nubX, nubY;

    public JoyStickView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        maxRadius = 62 * density;
        base = context.getResources().getDrawable(R.drawable.joystick_base, null);
        nub = context.getResources().getDrawable(R.drawable.joystick_nub, null);
        setFocusable(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int d = (int) Math.min(w, h);
        int baseSize = (int) (150 * density);
        int x = (w - baseSize) / 2;
        int y = (h - baseSize) / 2;
        base.setBounds(x, y, x + baseSize, y + baseSize);
        base.draw(canvas);

        int nubSize = (int) (92 * density);
        int nx = (int) (w / 2f + nubX - nubSize / 2f);
        int ny = (int) (h / 2f + nubY - nubSize / 2f);
        nub.setBounds(nx, ny, nx + nubSize, ny + nubSize);
        nub.draw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = ev.getX() - cx;
        float dy = ev.getY() - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len > maxRadius) {
            dx = dx / len * maxRadius;
            dy = dy / len * maxRadius;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                nubX = dx;
                nubY = dy;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                nubX = nubY = 0;
                break;
            default:
                return false;
        }
        invalidate();
        return true;
    }

    /** -1..1, positive to the right */
    public float getAxisX() {
        return nubX / maxRadius;
    }

    /** -1..1, positive when pushed up */
    public float getAxisY() {
        return -nubY / maxRadius;
    }
}