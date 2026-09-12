package com.voxelcraft.ui;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.voxelcraft.R;
import com.voxelcraft.game.Blocks;
import com.voxelcraft.render.GameRenderer;

public final class GameView extends FrameLayout {

    private final GLSurfaceView gl;
    private final GameRenderer renderer = new GameRenderer();
    private final JoyStickView joystick;

    private static final int[] HOTBAR_BLOCKS = {
            Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.SAND, Blocks.WOOD, Blocks.LEAVES
    };
    private static final int[] HOTBAR_COLORS = {
            0xFF5E9C32, 0xFF8A5A2B, 0xFF7A7F85, 0xFFE7D8A0, 0xFF6B4522, 0xFF4C8C2C
    };
    private final View[] slotViews = new View[HOTBAR_BLOCKS.length];
    private int selectedIndex = 0;

    public GameView(Context context) {
        super(context);

        gl = new GLSurfaceView(context);
        gl.setEGLContextClientVersion(2);
        gl.setRenderer(renderer);
        gl.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        addView(gl, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FrameLayout overlay = new FrameLayout(context);
        addView(overlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- look area (anywhere away from controls) ----
        View look = new View(context);
        look.setOnTouchListener(new LookListener());
        overlay.addView(look, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- move joystick (bottom-left) ----
        joystick = new JoyStickView(context);
        joystick.setOnTouchListener((v, ev) -> {
            renderer.setJoystick(joystick.getAxisX(), joystick.getAxisY());
            return false;
        });
        LayoutParams jlp = new LayoutParams(dp(190), dp(190));
        jlp.gravity = Gravity.BOTTOM | Gravity.START;
        jlp.leftMargin = dp(14);
        jlp.bottomMargin = dp(14);
        overlay.addView(joystick, jlp);

        // ---- action buttons (bottom-right) ----
        Button jump = makeRoundButton("JUMP", dp(92), 20);
        LayoutParams jumpLp = new LayoutParams(dp(92), dp(92));
        jumpLp.gravity = Gravity.BOTTOM | Gravity.END;
        jumpLp.rightMargin = dp(18);
        jumpLp.bottomMargin = dp(24);
        jump.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    renderer.setJumpHeld(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    renderer.setJumpHeld(false);
                    return true;
                default:
                    return false;
            }
        });
        overlay.addView(jump, jumpLp);

        Button place = makeRoundButton("PLACE", dp(68), 14);
        LayoutParams placeLp = new LayoutParams(dp(68), dp(68));
        placeLp.gravity = Gravity.BOTTOM | Gravity.END;
        placeLp.rightMargin = dp(118);
        placeLp.bottomMargin = dp(150);
        place.setOnClickListener(v -> renderer.queuePlace());
        overlay.addView(place, placeLp);

        Button breakBtn = makeRoundButton("BREAK", dp(68), 14);
        LayoutParams breakLp = new LayoutParams(dp(68), dp(68));
        breakLp.gravity = Gravity.BOTTOM | Gravity.END;
        breakLp.rightMargin = dp(30);
        breakLp.bottomMargin = dp(150);
        breakBtn.setOnClickListener(v -> renderer.queueBreak());
        overlay.addView(breakBtn, breakLp);

        // ---- hotbar (bottom-center) ----
        LinearLayout bar = new LinearLayout(context);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        for (int i = 0; i < HOTBAR_BLOCKS.length; i++) {
            LinearLayout slot = new LinearLayout(context);
            slot.setBackgroundResource(R.drawable.slot_normal);
            slot.setPadding(dp(4), dp(4), dp(4), dp(4));

            View colorBox = new View(context);
            colorBox.setBackgroundColor(HOTBAR_COLORS[i]);
            colorBox.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
            slot.addView(colorBox);

            final int index = i;
            slot.setOnClickListener(v -> selectBlock(index));
            bar.addView(slot, new LinearLayout.LayoutParams(dp(54), dp(54)));

            slotViews[i] = slot;
        }
        LayoutParams barLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        barLp.bottomMargin = dp(8);
        overlay.addView(bar, barLp);

        selectBlock(0);
    }

    private void selectBlock(int index) {
        selectedIndex = index;
        renderer.selectBlock(HOTBAR_BLOCKS[index]);
        for (int i = 0; i < slotViews.length; i++) {
            slotViews[i].setBackgroundResource(i == selectedIndex ? R.drawable.slot_selected : R.drawable.slot_normal);
        }
    }

    private Button makeRoundButton(String text, int size, int textSp) {
        Button b = new Button(getContext());
        b.setText(text);
        b.setTextSize(textSp);
        b.setTextColor(Color.WHITE);
        b.setBackgroundResource(R.drawable.btn_round);
        b.setPadding(0, 0, 0, 0);
        b.setLayoutParams(new LayoutParams(size, size));
        return b;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    public void onResume() {
        gl.onResume();
    }

    public void onPause() {
        gl.onPause();
    }

    private class LookListener implements View.OnTouchListener {
        private float lastX, lastY;

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = ev.getX();
                    lastY = ev.getY();
                    return ev.getX() > v.getWidth() * 0.36f;
                case MotionEvent.ACTION_MOVE:
                    if (ev.getX() <= v.getWidth() * 0.36f) return false;
                    float dx = ev.getX() - lastX;
                    float dy = ev.getY() - lastY;
                    lastX = ev.getX();
                    lastY = ev.getY();
                    renderer.lookDelta(dx, dy);
                    return true;
                default:
                    return false;
            }
        }
    }
}