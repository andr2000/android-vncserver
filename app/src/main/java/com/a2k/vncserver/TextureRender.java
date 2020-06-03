package com.a2k.vncserver;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLU;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

class TextureRender {
    private static final String TAG = MainActivity.TAG;

    private int mWidth;
    private int mHeight;

    public void setHeightOffset(int mHeightOffset) {
        this.mHeightOffset = mHeightOffset;
    }

    private int mHeightOffset;

    private EGL10 mEgl;
    private EGLDisplay mEglDisplay;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;

    private static final int TEX_SURFACE_TEXTURE = 0;
    private static final int TEX_RENDER_TEXTURE = 1;
    private static final int TEX_NUMBER = 2;
    private int[] mEglTextures = new int[TEX_NUMBER];
    private int mFrameBuffer;
    private int mDepthBuffer;
    private int mPixelFormat;

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesData = {
            /* X      Y  Z,   U    V */
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f, 1.0f, 0, 0.f, 1.f,
            1.0f, 1.0f, 0, 1.f, 1.f,
    };

    private final float[] mSTMatrix = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
    };

    private FloatBuffer mTriangleVertices;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * aPosition;\n" +
                    "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

    private int[] getConfig() {
        if (mPixelFormat == GLES20.GL_RGB565) {
            return new int[]{
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 5,
                    EGL10.EGL_GREEN_SIZE, 6,
                    EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_ALPHA_SIZE, 0,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
        } else if (mPixelFormat == GLES20.GL_RGBA) {
            return new int[]{
                    EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
        }
        Log.d(TAG, "Unsupported pixel format " + mPixelFormat);
        return null;
    }

    private float[] mMVPMatrix = new float[16];

    private int mProgram;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private VncJni mVncJni;

    public TextureRender(VncJni vncJni, int width, int height,
                         int pixelFormat) {
        mVncJni = vncJni;
        mWidth = width;
        mHeight = height;
        mPixelFormat = pixelFormat;
        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);
        setHeightOffset(0);

        Matrix.setIdentityM(mSTMatrix, 0);
    }

    public int getTextureId() {
        return mEglTextures[TEX_SURFACE_TEXTURE];
    }

    public void drawFrame() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
        draw();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mEglTextures[TEX_RENDER_TEXTURE]);
        mVncJni.bindNextGraphicBuffer();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        mVncJni.frameAvailable();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void draw() {
        checkGlError("onDrawFrame start");

        GLES20.glViewport(0, -mHeightOffset, mWidth, mHeight + mHeightOffset * 2);
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, getTextureId());

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");
        GLES20.glFinish();
    }

    public void swapBuffers() {
        mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);
    }

    private EGLConfig chooseEglConfig() {
        int[] configsCount = new int[1];
        int[] configSpec = getConfig();

        /* get number of configurations */
        if (!mEgl.eglChooseConfig(mEglDisplay, null, null, 0, configsCount)) {
            throw new IllegalArgumentException("Failed to get number of configs: " +
                    GLU.gluErrorString(mEgl.eglGetError()));
        }
        /* read all */
        EGLConfig[] configs = new EGLConfig[configsCount[0]];
        if (!mEgl.eglChooseConfig(mEglDisplay, null, configs, configsCount[0], configsCount)) {
            throw new IllegalArgumentException("Failed to choose config: " +
                    GLU.gluErrorString(mEgl.eglGetError()));
        } else if (configsCount[0] > 0) {
            /* find number of pairs in the configuration we want */
            int numPairs = 0;
            while (configSpec[numPairs] != EGL10.EGL_NONE) {
                numPairs += 2;
            }
            numPairs /= 2;
            /* find configuration that suits */
            int[] attr = new int[1];
            for (int i = 0; i < configsCount[0]; i++) {
                int match = 0;
                for (int j = 0, idx = 0; j < numPairs; j++, idx += 2) {
                    mEgl.eglGetConfigAttrib(mEglDisplay, configs[i], configSpec[idx], attr);
                    if (configSpec[idx] == EGL10.EGL_SURFACE_TYPE) {
                        if ((attr[0] & configSpec[idx + 1]) == configSpec[idx + 1]) {
                            match++;
                        } else {
                            break;
                        }
                    } else if (configSpec[idx] == EGL10.EGL_RENDERABLE_TYPE) {
                        if ((attr[0] & configSpec[idx + 1]) == configSpec[idx + 1]) {
                            match++;
                        } else {
                            break;
                        }
                    } else if (attr[0] == configSpec[idx + 1]) {
                        match++;
                    } else {
                        break;
                    }
                }
                if (match == numPairs) {
                    return configs[i];
                }
            }
            return null;
        }
        return null;
    }

    private void dumpConfig(EGLConfig eglConfig) {
        int[] att = new int[1];
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_SURFACE_TYPE, att);
        Log.d(TAG, "EGL_SURFACE_TYPE " + att[0]);
        if ((att[0] & EGL10.EGL_WINDOW_BIT) != EGL10.EGL_WINDOW_BIT) {
            Log.d(TAG, "Failed to choose EGL_WINDOW_BIT");
        }
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_RENDERABLE_TYPE, att);
        Log.d(TAG, "EGL_RENDERABLE_TYPE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_RED_SIZE, att);
        Log.d(TAG, "EGL_RED_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_GREEN_SIZE, att);
        Log.d(TAG, "EGL_GREEN_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_BLUE_SIZE, att);
        Log.d(TAG, "EGL_BLUE_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_ALPHA_SIZE, att);
        Log.d(TAG, "EGL_ALPHA_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_DEPTH_SIZE, att);
        Log.d(TAG, "EGL_DEPTH_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_CONFIG_ID, att);
        Log.d(TAG, "EGL_CONFIG_ID " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_STENCIL_SIZE, att);
        Log.d(TAG, "EGL_STENCIL_SIZE " + att[0]);
        mEgl.eglGetConfigAttrib(mEglDisplay, eglConfig, EGL10.EGL_BUFFER_SIZE, att);
        Log.d(TAG, "EGL_BUFFER_SIZE " + att[0]);
    }

    private EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
        int[] attribList =
                {
                        EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL10.EGL_NONE
                };
        return mEgl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attribList);
    }

    private int createFrameBuffer(int width, int height, int targetTextureId, int depthBuffer) {
        int framebuffer;
        int[] framebuffers = new int[1];
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        framebuffer = framebuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);

        int depthbuffer;
        int[] renderbuffers = new int[1];
        GLES20.glGenRenderbuffers(1, renderbuffers, 0);
        depthbuffer = renderbuffers[0];

        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthbuffer);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER,
                GLES20.GL_DEPTH_COMPONENT16, width, height);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthbuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D,
                targetTextureId, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer is not complete: " +
                    GLU.gluErrorString(status));
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return framebuffer;
    }

    private void deleteFrameBuffer() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBuffer);
        int[] renderbuffers = new int[1];
        renderbuffers[0] = mDepthBuffer;
        GLES20.glDeleteRenderbuffers(1, renderbuffers, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        int[] framebuffers = new int[1];
        framebuffers[0] = mFrameBuffer;
        GLES20.glDeleteFramebuffers(1, framebuffers, 0);
    }

    private void initGL(int width, int height, int pixelFormat) {
        mEgl = (EGL10) EGLContext.getEGL();
        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        mEgl.eglInitialize(mEglDisplay, version);

        EGLConfig eglConfig = chooseEglConfig();
        mEglContext = createContext(mEgl, mEglDisplay, eglConfig);
        dumpConfig(eglConfig);
        /* we always render to FBO */
        int surfaceAttribs[] =
                {
                        EGL10.EGL_WIDTH, 1,
                        EGL10.EGL_HEIGHT, 1,
                        EGL10.EGL_NONE
                };
        mEglSurface = mEgl.eglCreatePbufferSurface(mEglDisplay, eglConfig, surfaceAttribs);
        if ((mEglSurface == null) || (mEglSurface == EGL10.EGL_NO_SURFACE)) {
            throw new RuntimeException("GL Error: 0x" + Integer.toHexString(mEgl.eglGetError()));
        }
        if (!mEgl.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            throw new RuntimeException("GL Make current error: 0x" + Integer.toHexString(mEgl.eglGetError()));
        }
        /* Generate textures */
        GLES20.glGenTextures(TEX_NUMBER, mEglTextures, 0);
        checkGlError("Textures generated");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mEglTextures[TEX_RENDER_TEXTURE]);
        checkGlError("glBindTexture TEX_RENDER_TEXTURE");
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameter");
        mVncJni.bindNextGraphicBuffer();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        mFrameBuffer = createFrameBuffer(width, height, mEglTextures[TEX_RENDER_TEXTURE], mDepthBuffer);
        Log.d(TAG, "OpenGL initialized");
    }

    private void deinitGL() {
        GLES20.glDeleteTextures(TEX_NUMBER, mEglTextures, 0);
        GLES20.glDeleteProgram(mProgram);
        mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        mEgl.eglDestroyContext(mEglDisplay, mEglContext);
        mEgl.eglTerminate(mEglDisplay);
        Log.d(TAG, "OpenGL deinitialized");
    }

    public void start() {
        initGL(mWidth, mHeight, mPixelFormat);
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }
    }

    public void stop() {
        deleteFrameBuffer();
        deinitGL();
    }

    public void changeFragmentShader(String fragmentShader) {
        GLES20.glDeleteProgram(mProgram);
        mProgram = createProgram(VERTEX_SHADER, fragmentShader);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError 0x" + Integer.toHexString(error));
            throw new RuntimeException(op + ": glError " + GLU.gluErrorString(error));
        }
    }
}
