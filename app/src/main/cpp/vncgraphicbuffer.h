#ifndef VNCGRAPHICBBUFFER_H_
#define VNCGRAPHICBBUFFER_H_

#include <android/hardware_buffer_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <stdint.h>

class VncGraphicBuffer {
public:
    VncGraphicBuffer(int width, int height, uint32_t format);

    virtual ~VncGraphicBuffer();

    bool allocate();

    bool bind();

    int lock(unsigned char **bits);

    int unlock();

    int getWidth() { return mWidth; }

    int getHeight() { return mHeight; }

    int getStride() { return mStride; }

private:
    uint32_t mWidth;
    uint32_t mHeight;
    uint32_t mUsage;
    uint32_t mFormat;
    uint32_t mStride;
    AHardwareBuffer *mHandle;
    EGLImageKHR mEGLImage;

    void clearGLError();

    bool ensureNoGLError(const char *name);

};

#endif /* VNCGRAPHICBBUFFER_H_ */
