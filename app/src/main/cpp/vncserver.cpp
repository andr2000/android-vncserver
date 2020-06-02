#include <GLES2/gl2.h>

#include "log.h"
#include "vncserver.h"

VncServer::VncServer() = default;

VncServer::~VncServer()
{
	cleanup();
}

void VncServer::cleanup()
{
	m_Terminated = true;
	if (m_WorkerThread.joinable())
	{
		m_WorkerThread.join();
	}
	if (m_RfbScreenInfoPtr)
	{
		rfbShutdownServer(m_RfbScreenInfoPtr, true);
		m_RfbScreenInfoPtr = nullptr;
	}
	m_Width = 0;
	m_Height = 0;
	m_PixelFormat = 0;
}

void VncServer::setJavaVM(JavaVM *javaVM)
{
	m_JavaVM = javaVM;
}

void VncServer::setupNotificationClb(JNIEnv *env, jobject jObject, jclass jClass)
{
	m_Object = jObject;
	m_Class = jClass;
	m_NotificationClb = env->GetMethodID(m_Class, "onNotification", "(ILjava/lang/String;)V");
	if (!m_NotificationClb)
	{
		LOGE("Failed to get method ID for onNotification");
	}
}

const std::string VncServer::getVersion()
{
	return "libvncserver " LIBVNCSERVER_VERSION;
}

void VncServer::postEventToUI(int what, std::string text)
{
	LOGD("postEventToUI what = %d, text = %s", what, text.c_str());
	if (m_NotificationClb)
	{
		JNIEnv *env;
		m_JavaVM->GetEnv((void **)&env, JNI_VERSION_1_4);
		m_JavaVM->AttachCurrentThread(&env, 0);
		jvalue jpar[2];
		jpar[0].i = what;
		jpar[1].l = env->NewStringUTF(text.c_str());
		env->CallVoidMethodA(m_Object, m_NotificationClb, jpar);
		env->DeleteLocalRef(jpar[1].l);
	}
	else
	{
		LOGE("onNotification method ID was not set");
	}
}

static char frame_buffer[800 * 480 *2];

void VncServer::setVncFramebuffer()
{
    m_RfbScreenInfoPtr->frameBuffer = frame_buffer;
}

rfbScreenInfoPtr VncServer::getRfbScreenInfoPtr()
{
	rfbScreenInfoPtr scr = nullptr;
	int argc = 0;
	switch (m_PixelFormat)
	{
		case GL_RGB565:
		{
			scr = rfbGetScreen(&argc, nullptr, m_Width , m_Height, 0 /* not used */ ,
				3, 2);
			scr->serverFormat.redShift = 11;
			scr->serverFormat.greenShift = 5;
			scr->serverFormat.blueShift = 0;
			scr->serverFormat.bitsPerPixel = 16;
			scr->serverFormat.trueColour = true;
			scr->serverFormat.redMax = 31;
			scr->serverFormat.greenMax = 63;
			scr->serverFormat.blueMax = 31;
			break;
		}
		case GL_RGBA:
		{
			scr = rfbGetScreen(&argc, nullptr, m_Width , m_Height, 0 /* not used */ ,
				3, 4);
			scr->serverFormat.redShift = 0;
			scr->serverFormat.greenShift = 8;
			scr->serverFormat.blueShift = 16;
			scr->serverFormat.bitsPerPixel = 32;
			scr->serverFormat.trueColour = true;
			scr->serverFormat.redMax = 255;
			scr->serverFormat.greenMax = 255;
			scr->serverFormat.blueMax = 255;
			break;
		}
	}
	return scr;
}

void clientGoneClb(rfbClientPtr cl)
{
	return VncServer::getInstance().clientGone(cl);
}

void VncServer::clientGone(rfbClientPtr cl)
{
	LOGD("Client disconnected: %s", cl->host);
	postEventToUI(CLIENT_DISCONNECTED, std::string(cl->host));
}

rfbNewClientAction clientHookClb(rfbClientPtr cl)
{
	cl->clientGoneHook=(ClientGoneHookPtr)clientGoneClb;
	return VncServer::getInstance().clientHook(cl);
}

rfbNewClientAction VncServer::clientHook(rfbClientPtr cl)
{
	LOGD("Client connected: %s", cl->host);
	postEventToUI(CLIENT_CONNECTED, std::string(cl->host));
	return RFB_CLIENT_ACCEPT;
}

void handlePointerEventClb(int buttonMask, int x, int y, rfbClientPtr cl)
{
}

void VncServer::handlePointerEvent(int buttonMask, int x, int y, rfbClientPtr cl)
{
}

void handleKeyEventClb(rfbBool down, rfbKeySym key, rfbClientPtr cl)
{
}

void VncServer::handleKeyEvent(rfbBool down, rfbKeySym key, rfbClientPtr cl)
{
}

void rfbDefaultLog(const char *format, ...)
{
	va_list args;
	char buf[256];
	va_start(args, format);
	vsprintf(buf, format, args);
	LOGI("%s", buf);
	va_end(args);
}

int VncServer::startServer(int width, int height, int pixelFormat, bool fullFrameUpdate)
{
	m_Width = width;
	m_Height = height;
	m_PixelFormat = pixelFormat;
	LOGI("Starting VNC server (%dx%d), %s, using %sfull screen updates", m_Width, m_Height, m_PixelFormat == GL_RGB565 ? "RGB565" : "RGBA",
		fullFrameUpdate ? "" : "no ");

	m_RfbScreenInfoPtr = getRfbScreenInfoPtr();
	if (m_RfbScreenInfoPtr == nullptr)
	{
		LOGE("Failed to get RFB screen");
		return -1;
	}
	m_RfbScreenInfoPtr->desktopName = DESKTOP_NAME;
	m_RfbScreenInfoPtr->newClientHook = (rfbNewClientHookPtr)clientHookClb;

	m_RfbScreenInfoPtr->handleEventsEagerly = true;
	m_RfbScreenInfoPtr->deferUpdateTime = 0;
	m_RfbScreenInfoPtr->port = VNC_PORT;
	m_RfbScreenInfoPtr->alwaysShared = true;
	m_RfbScreenInfoPtr->neverShared = false;

	setVncFramebuffer();

	rfbLogEnable(true);
	rfbLog = rfbDefaultLog;
	rfbErr = rfbDefaultLog;
	rfbInitServer(m_RfbScreenInfoPtr);

	m_Terminated = false;
	m_WorkerThread = std::thread(&VncServer::worker, this);
	postEventToUI(SERVER_STARTED, "VNC server started");
	return 0;
}

int VncServer::stopServer()
{
	cleanup();
	postEventToUI(SERVER_STOPPED, "VNC server stopped");
	return 0;
}

void VncServer::worker()
{
	while (!m_Terminated)
	{
		rfbProcessEvents(m_RfbScreenInfoPtr, 5000);
	}
	if (m_JavaVM)
	{
		m_JavaVM->DetachCurrentThread();
	}
}
