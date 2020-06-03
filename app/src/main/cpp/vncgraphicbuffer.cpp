#define GL_GLEXT_PROTOTYPES
#define EGL_EGLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <EGL/eglext.h>
#include <string>

#include "log.h"
#include "vncgraphicbuffer.h"

VncGraphicBuffer::VncGraphicBuffer(int width, int height,
                                   uint32_t format) :
        mWidth(width), mHeight(height),
        mUsage(AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN),
        mFormat(format),
        mHandle(nullptr),
        mEGLImage(nullptr) {
}

VncGraphicBuffer::~VncGraphicBuffer() {
    if (mHandle)
        AHardwareBuffer_release(mHandle);
}

int VncGraphicBuffer::lock(unsigned char **bits) {
    return AHardwareBuffer_lock(mHandle, mUsage, -1, nullptr,
                                reinterpret_cast<void **>(bits));
}

int VncGraphicBuffer::unlock() {
    return AHardwareBuffer_unlock(mHandle, nullptr);
}

bool VncGraphicBuffer::allocate() {
    AHardwareBuffer_Desc desc;

    memset(&desc, 0, sizeof(desc));
    desc.width = mWidth;
    desc.height = mHeight;
    desc.format = mFormat;
    desc.usage = mUsage | AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER;
    desc.layers = 1;
    if (AHardwareBuffer_allocate(&desc, &mHandle) != 0)
        return false;
    AHardwareBuffer_describe(mHandle, &desc);
    mStride = desc.stride;

    EGLint eglImgAttrs[] = {
            EGL_IMAGE_PRESERVED_KHR,
            EGL_TRUE,
            EGL_NONE,
            EGL_NONE
    };

    EGLClientBuffer nativeBuffer = eglGetNativeClientBufferANDROID(mHandle);
    mEGLImage = eglCreateImageKHR(eglGetDisplay(EGL_DEFAULT_DISPLAY),
                                  EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
                                  nativeBuffer, eglImgAttrs);
    if (mEGLImage) {
        LOGD("Allocated graphic buffer (%dx%d), format %s, stride %d", mWidth,
             mHeight,
             mFormat == AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM ? "RGB565"
                                                            : "RGBA",
             mStride);
    }
    return mEGLImage != nullptr;

}

bool VncGraphicBuffer::bind() {
    clearGLError();
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, mEGLImage);
    return ensureNoGLError("glEGLImageTargetTexture2DOES");
}

void VncGraphicBuffer::clearGLError() {
    while (glGetError() != GL_NO_ERROR);
}

bool VncGraphicBuffer::ensureNoGLError(const char *name) {
    bool result = true;
    GLuint error;

    while ((error = glGetError()) != GL_NO_ERROR) {
        LOGE("GL error [%s]: %40x\n", name, error);
        result = false;
    }
    return result;
}
