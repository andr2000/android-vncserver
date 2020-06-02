#ifndef LIBVNCSERVER_VNCSERVER_H_
#define LIBVNCSERVER_VNCSERVER_H_

#include <atomic>
#include <array>
#include <jni.h>
#include <memory>
#include <mutex>
#include <thread>
#include <string>

#include "rfb/rfb.h"

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
	void worker();

	VncServer();

	void cleanup();

	rfbScreenInfoPtr m_RfbScreenInfoPtr;
	rfbScreenInfoPtr getRfbScreenInfoPtr();
	void setVncFramebuffer();
};

#endif /* LIBVNCSERVER_VNCSERVER_H_ */
