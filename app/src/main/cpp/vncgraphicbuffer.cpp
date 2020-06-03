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
	m_Width(width), m_Height(height),
	m_Usage(AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN),
	m_Format(format),
	m_Handle(nullptr),
	m_EGLImage(nullptr)
{
}

VncGraphicBuffer::~VncGraphicBuffer()
{
    if (m_Handle)
        AHardwareBuffer_release(m_Handle);
}

int VncGraphicBuffer::lock(unsigned char **bits)
{
    return AHardwareBuffer_lock(m_Handle, m_Usage, -1, nullptr,
                                reinterpret_cast<void **>(bits));
}

int VncGraphicBuffer::unlock()
{
    return AHardwareBuffer_unlock(m_Handle, nullptr);
}

bool VncGraphicBuffer::allocate()
{
    AHardwareBuffer_Desc desc;

    memset(&desc, 0, sizeof(desc));
    desc.width = m_Width;
    desc.height = m_Height;
    desc.format = m_Format;
    desc.usage = m_Usage | AHARDWAREBUFFER_USAGE_GPU_FRAMEBUFFER;
    desc.layers = 1;
    if (AHardwareBuffer_allocate(&desc, &m_Handle) != 0)
        return false;
    AHardwareBuffer_describe(m_Handle, &desc);
    m_Stride = desc.stride;

    EGLint eglImgAttrs[] = {
            EGL_IMAGE_PRESERVED_KHR,
            EGL_TRUE,
            EGL_NONE,
            EGL_NONE
    };

    EGLClientBuffer nativeBuffer = eglGetNativeClientBufferANDROID(m_Handle);
    m_EGLImage = eglCreateImageKHR(eglGetDisplay(EGL_DEFAULT_DISPLAY),
            EGL_NO_CONTEXT, EGL_NATIVE_BUFFER_ANDROID,
            nativeBuffer, eglImgAttrs);
    if (m_EGLImage) {
        LOGD("Allocated graphic buffer (%dx%d), format %s, stride %d", m_Width, m_Height,
             m_Format == AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM ? "RGB565" : "RGBA", m_Stride);
    }
    return m_EGLImage != nullptr;

}

bool VncGraphicBuffer::bind()
{
    clearGLError();
    glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, m_EGLImage);
    return ensureNoGLError("glEGLImageTargetTexture2DOES");
}

void VncGraphicBuffer::clearGLError()
{
    while (glGetError() != GL_NO_ERROR);
}

bool VncGraphicBuffer::ensureNoGLError(const char* name)
{
    bool result = true;
    GLuint error;

    while ((error = glGetError()) != GL_NO_ERROR)
    {
        LOGE("GL error [%s]: %40x\n", name, error);
        result = false;
    }
    return result;
}
