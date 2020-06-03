#ifndef VNCGRAPHICBBUFFER_H_
#define VNCGRAPHICBBUFFER_H_

#include <android/hardware_buffer_jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <stdint.h>

class VncGraphicBuffer
{
public:
	VncGraphicBuffer(int width, int height, uint32_t format);
	virtual ~VncGraphicBuffer();

    bool allocate();
    bool bind();
	int lock(unsigned char **bits);
	int unlock();

	int getWidth()  { return m_Width;  }
	int getHeight() { return m_Height; }
	int getStride() { return m_Stride; }

private:
	uint32_t m_Width;
	uint32_t m_Height;
	uint32_t m_Usage;
	uint32_t m_Format;
	uint32_t m_Stride;
    AHardwareBuffer *m_Handle;
	EGLImageKHR m_EGLImage;

	void clearGLError();
	bool ensureNoGLError(const char* name);

};

#endif /* VNCGRAPHICBBUFFER_H_ */
