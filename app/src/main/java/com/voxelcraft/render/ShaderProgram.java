package com.voxelcraft.render;

import android.opengl.GLES20;
import android.util.Log;

public final class ShaderProgram {

    public int program;
    public String name;

    public ShaderProgram(String name, String vertexSrc, String fragmentSrc) {
        this.name = name;
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e("VoxelCraft", name + " link failed: " + GLES20.glGetProgramInfoLog(program));
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
    }

    public void use() {
        GLES20.glUseProgram(program);
    }

    private static int compile(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String info = GLES20.glGetShaderInfoLog(shader);
            Log.e("VoxelCraft", "shader compile failed: " + info);
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }
}