#ifndef LIBVNCSERVER_VNCSERVER_H_
#define LIBVNCSERVER_VNCSERVER_H_

#include <atomic>
#include <array>
#include <jni.h>
#include <memory>
#include <mutex>
#include <thread>
#include <string>

#include "buffermanager.h"
#include "rfb/rfb.h"
#include "vncgraphicbuffer.h"

class VncServer
{
public:
	enum UI_EVENT
	{
		SERVER_STARTED,
		SERVER_STOPPED,
		CLIENT_CONNECTED,
		CLIENT_DISCONNECTED,
	};

	static VncServer &getInstance()
	{
		static VncServer m_Instance;
		return m_Instance;
	}

	~VncServer();

	void setJavaVM(JavaVM *javaVM);
	void setupNotificationClb(JNIEnv *env, jobject jObject, jclass jClass);

	const std::string getVersion();

	void postEventToUI(int what, std::string text);

	int startServer(int width, int height, int pixelFormat, bool fullFrameUpdate);
	int stopServer();
	void bindNextProducerBuffer();
	void frameAvailable();
	void onRotation(int rotation);

	rfbNewClientAction clientHook(rfbClientPtr cl);
	void clientGone(rfbClientPtr cl);
	void handlePointerEvent(int buttonMask, int x, int y, rfbClientPtr cl);
	void handleKeyEvent(rfbBool down, rfbKeySym key, rfbClientPtr cl);

private:
	static const bool DUMP_ENABLED { false };
	const char *DESKTOP_NAME = "Android";
	const int VNC_PORT = 5901;
	JavaVM *m_JavaVM;
	jobject m_Object;
	jclass m_Class;
	jmethodID m_NotificationClb;

	int m_Width;
	int m_Height;
	int m_PixelFormat;

	int m_Rotation;

	std::thread m_WorkerThread;
	std::atomic<bool> m_Terminated { true };
	std::mutex m_FrameAvailableLock;
	bool m_FrameAvailable { false };
	void worker();

	VncServer();

	void cleanup();

	VncGraphicBuffer *m_VncBuffer { nullptr };
	VncGraphicBuffer *m_CmpBuffer { nullptr };
	VncGraphicBuffer *m_GlBuffer { nullptr };
	std::unique_ptr<BufferManager> m_BufferManager;
	bool allocateBuffers(int width, int height, int pixelFormat, int fullFrameUpdate);
	void releaseBuffers();

	rfbScreenInfoPtr m_RfbScreenInfoPtr;
	rfbScreenInfoPtr getRfbScreenInfoPtr();
	void setVncFramebuffer();
	void dumpFrame(char *buffer);
	void compare(int width, int shift, uint32_t *buffer0, uint32_t *buffer1);
};

#endif /* LIBVNCSERVER_VNCSERVER_H_ */
