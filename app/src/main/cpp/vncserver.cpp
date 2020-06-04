#include <GLES2/gl2.h>

#include "log.h"
#include "vncserver.h"

VncServer::VncServer() = default;

VncServer::~VncServer() {
    cleanup();
}

void VncServer::cleanup() {
    mTerminated = true;
    if (mWorkerThread.joinable()) {
        mWorkerThread.join();
    }
    if (mRfbScreenInfoPtr) {
        rfbShutdownServer(mRfbScreenInfoPtr, true);
        mRfbScreenInfoPtr = nullptr;
    }
    releaseBuffers();
    mWidth = 0;
    mHeight = 0;
    mPixelFormat = 0;
    mFrameAvailable = false;
}

void VncServer::setJavaVM(JavaVM *javaVM) {
    mJavaVM = javaVM;
}

void
VncServer::setupNotificationClb(JNIEnv *env, jobject jObject, jclass jClass) {
    mObject = jObject;
    mClass = jClass;
    mNotificationClb = env->GetMethodID(mClass, "onNotification",
                                        "(ILjava/lang/String;)V");
    if (!mNotificationClb) {
        LOGE("Failed to get method ID for onNotification");
    }
    mNotificationTouchClb = env->GetMethodID(mClass, "onNotificationTouch",
                                             "(III)V");

    mNotificationKbdClb = env->GetMethodID(mClass, "onNotificationKbd",
                                             "(II)V");
}

const std::string VncServer::getVersion() {
    return "libvncserver " LIBVNCSERVER_VERSION;
}

void VncServer::postEventToUI(int what, std::string text) {
    LOGD("postEventToUI what = %d, text = %s", what, text.c_str());
    if (mNotificationClb) {
        JNIEnv *env;
        mJavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
        mJavaVM->AttachCurrentThread(&env, 0);
        jvalue jpar[2];
        jpar[0].i = what;
        jpar[1].l = env->NewStringUTF(text.c_str());
        env->CallVoidMethodA(mObject, mNotificationClb, jpar);
        env->DeleteLocalRef(jpar[1].l);
    } else {
        LOGE("onNotification method ID was not set");
    }
}

bool VncServer::allocateBuffers(int width, int height, int pixelFormat,
                                int fullFrameUpdate) {
    int format = 0;
    switch (pixelFormat) {
        case GL_RGBA: {
            format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
            break;
        }
        case GL_RGB565: {
            format = AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM;
            break;
        }
        default: {
            LOGE("Unsupported pixel format");
            return false;
        }
    }
    mBufferManager.reset(new BufferManager());
    return mBufferManager->allocate(fullFrameUpdate ? BufferManager::TRIPLE
                                                    : BufferManager::WITH_COMPARE,
                                    width, height, format);
}

void VncServer::releaseBuffers() {
    mBufferManager.reset();
    mVncBuffer = nullptr;
    mGlBuffer = nullptr;
    mCmpBuffer = nullptr;
}

void VncServer::setVncFramebuffer() {
    if (mVncBuffer) {
        if (mVncBuffer->unlock() != 0) {
            LOGE("Failed to unlock buffer");
        }
    }
    mBufferManager->getConsumer(mVncBuffer, mCmpBuffer);
    if (mVncBuffer) {
        unsigned char *vncbuf;
        if (mVncBuffer->lock(&vncbuf) != 0) {
            LOGE("Failed to lock buffer");
        }
        mRfbScreenInfoPtr->frameBuffer = reinterpret_cast<char *>(vncbuf);
    } else {
        LOGE("Failed to get new framebuffer");
    }
}

void VncServer::bindNextProducerBuffer() {
    mGlBuffer = mBufferManager->getProducer();
    if (mGlBuffer) {
        if (!mGlBuffer->bind()) {
            LOGE("Failed to bind graphics buffer");
        }
    }
}

rfbScreenInfoPtr VncServer::getRfbScreenInfoPtr() {
    rfbScreenInfoPtr scr = nullptr;
    int argc = 0;
    switch (mPixelFormat) {
        case GL_RGB565: {
            scr = rfbGetScreen(&argc, nullptr, mWidth, mHeight,
                               0 /* not used */ ,
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
        case GL_RGBA: {
            scr = rfbGetScreen(&argc, nullptr, mWidth, mHeight,
                               0 /* not used */ ,
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

void clientGoneClb(rfbClientPtr cl) {
    return VncServer::getInstance().clientGone(cl);
}

void VncServer::clientGone(rfbClientPtr cl) {
    LOGD("Client disconnected: %s", cl->host);
    postEventToUI(CLIENT_DISCONNECTED, std::string(cl->host));
}

rfbNewClientAction clientHookClb(rfbClientPtr cl) {
    cl->clientGoneHook = (ClientGoneHookPtr) clientGoneClb;
    return VncServer::getInstance().clientHook(cl);
}

rfbNewClientAction VncServer::clientHook(rfbClientPtr cl) {
    LOGD("Client connected: %s", cl->host);
    postEventToUI(CLIENT_CONNECTED, std::string(cl->host));
    return RFB_CLIENT_ACCEPT;
}

void handlePointerEventClb(int buttonMask, int x, int y, rfbClientPtr cl) {
    VncServer::getInstance().handlePointerEvent(buttonMask, x, y, cl);
}

void VncServer::postTouchEventToUI(int buttonMask, int x, int y) {
    if (mNotificationTouchClb) {
        JNIEnv *env;
        mJavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
        mJavaVM->AttachCurrentThread(&env, 0);
        jvalue jpar[3];
        jpar[0].i = buttonMask;
        jpar[1].i = x;
        jpar[2].i = y;
        env->CallVoidMethodA(mObject, mNotificationTouchClb, jpar);
    } else {
        LOGE("onNotificationTouch method ID was not set");
    }
}

void
VncServer::handlePointerEvent(int buttonMask, int x, int y, rfbClientPtr cl) {
    postTouchEventToUI(buttonMask, x, y);
}


void handleKeyEventClb(rfbBool down, rfbKeySym key, rfbClientPtr cl) {
    VncServer::getInstance().handleKeyEvent(down, key, cl);
}

void VncServer::postKbdEventToUI(rfbBool down, rfbKeySym key) {
    if (mNotificationKbdClb) {
        JNIEnv *env;
        mJavaVM->GetEnv((void **) &env, JNI_VERSION_1_4);
        mJavaVM->AttachCurrentThread(&env, 0);
        jvalue jpar[2];
        jpar[0].i = down;
        jpar[1].i = key;
        env->CallVoidMethodA(mObject, mNotificationKbdClb, jpar);
    } else {
        LOGE("onNotificationKbd method ID was not set");
    }
}

void VncServer::handleKeyEvent(rfbBool down, rfbKeySym key, rfbClientPtr cl) {
    postKbdEventToUI(down, key);
}

void rfbDefaultLog(const char *format, ...) {
    va_list args;
    char buf[256];
    va_start(args, format);
    vsprintf(buf, format, args);
    LOGD("%s", buf);
    va_end(args);
}

int VncServer::startServer(int width, int height, int pixelFormat,
                           bool fullFrameUpdate) {
    mWidth = width;
    mHeight = height;
    mPixelFormat = pixelFormat;
    LOGI("Starting VNC server (%dx%d), %s, using %sfull screen updates",
         mWidth, mHeight, mPixelFormat == GL_RGB565 ? "RGB565" : "RGBA",
         fullFrameUpdate ? "" : "no ");

    if (!allocateBuffers(mWidth, mHeight, mPixelFormat, fullFrameUpdate)) {
        return -1;
    }

    mRfbScreenInfoPtr = getRfbScreenInfoPtr();
    if (mRfbScreenInfoPtr == nullptr) {
        LOGE("Failed to get RFB screen");
        return -1;
    }
    mRfbScreenInfoPtr->desktopName = DESKTOP_NAME;
    mRfbScreenInfoPtr->newClientHook = (rfbNewClientHookPtr) clientHookClb;

    mRfbScreenInfoPtr->handleEventsEagerly = true;
    mRfbScreenInfoPtr->deferUpdateTime = 0;
    mRfbScreenInfoPtr->port = VNC_PORT;
    mRfbScreenInfoPtr->alwaysShared = true;
    mRfbScreenInfoPtr->neverShared = false;

    setVncFramebuffer();

    mRfbScreenInfoPtr->paddedWidthInBytes = mVncBuffer->getStride() *
                                            mRfbScreenInfoPtr->serverFormat.bitsPerPixel /
                                            CHAR_BIT;

    rfbLogEnable(true);
    rfbLog = rfbDefaultLog;
    rfbErr = rfbDefaultLog;
    rfbInitServer(mRfbScreenInfoPtr);

    mRfbScreenInfoPtr->kbdAddEvent = handleKeyEventClb;
    mRfbScreenInfoPtr->ptrAddEvent = handlePointerEventClb;

    mTerminated = false;
    mWorkerThread = std::thread(&VncServer::worker, this);
    postEventToUI(SERVER_STARTED, "VNC server started");
    return 0;
}

int VncServer::stopServer() {
    cleanup();
    postEventToUI(SERVER_STOPPED, "VNC server stopped");
    return 0;
}

void VncServer::frameAvailable() {
    std::lock_guard<std::mutex> lock(mFrameAvailableLock);
    mFrameAvailable = true;
}

void VncServer::dumpFrame(char *buffer) {
    const char *fName = "/sdcard/framebuffer.data";
    FILE *f = fopen(fName, "w+b");
    if (f) {
        int bytesPerPixel = mPixelFormat == GL_RGBA ? 4 : 2;
        fwrite(buffer, 1, mWidth * mHeight * bytesPerPixel, f);
        fclose(f);
        LOGD("Frame saved at %s", fName);
    } else {
        LOGE("Failed to save frame at %s", fName);
    }
}

void
VncServer::compare(int width, int shift, uint32_t *buffer0, uint32_t *buffer1) {
    const int DEFAULT_MIN = 10000;
    const int DEFAULT_MAX = -1;
    int maxX = DEFAULT_MAX, maxY = DEFAULT_MAX, minX = DEFAULT_MIN, minY = DEFAULT_MIN;

    int j = 0, i = 0;
    uint32_t *bufEnd = buffer0 + mHeight * width;
    /* find first pixel which differs */
    while (buffer0 < bufEnd) {
        if (*buffer0++ != *buffer1++) {
            /* buffers have already advanced their positions, so update counters as well */
            minX = maxX = i++;
            minY = maxY = j++;
            if (++i >= width) {
                i = 0;
                j++;
            }
            break;
        }
        if (++i >= width) {
            i = 0;
            j++;
        }
    };
    /* minY found */
    while (buffer0 < bufEnd) {
        if (*buffer0++ != *buffer1++) {
            if (i <= minX) {
                /* can skip delta, because it is already known to be in dirty region */
                minX = i;
                int delta = maxX - minX;
                buffer0 += delta;
                buffer1 += delta;
                i += delta;
                if (delta == width - 1) {
                    /* minX == 0, maxX == width - now find maxY */
                    i = 0;
                    j++;
                    while (buffer0 < bufEnd) {
                        if (*buffer0++ != *buffer1++) {
                            maxY = j;
                            int delta = width - i - 1;
                            buffer0 += delta;
                            buffer1 += delta;
                            i += delta;
                        }
                        if (++i >= width) {
                            i = 0;
                            j++;
                        }
                    };
                    break;
                }
            }
            if (i > maxX) {
                maxX = i;
            }
            if (j > maxY) {
                maxY = j;
            }
        }
        if (++i >= width) {
            i = 0;
            j++;
        }
    };
    /* first pixel which differ will set all min/max, so check any of those */
    if (minX != DEFAULT_MIN) {
        if (minX) {
            minX--;
        }
        if (minY) {
            minY--;
        }
        if (maxX < width) {
            maxX++;
        }
        if (maxY < mHeight) {
            maxY++;
        }
        if (shift) {
            minX <<= shift;
            maxX <<= shift;
        }
        rfbMarkRectAsModified(mRfbScreenInfoPtr, minX, minY, maxX, maxY);
    }
}

void VncServer::worker() {
    while ((!mTerminated) && rfbIsActive(mRfbScreenInfoPtr)) {
        rfbProcessEvents(mRfbScreenInfoPtr, 5000);
        bool update = false;
        {
            std::lock_guard<std::mutex> lock(mFrameAvailableLock);
            if (mFrameAvailable) {
                mFrameAvailable = false;
                update = true;
                setVncFramebuffer();
            }
        }
        if (!update) {
            continue;
        }
        update = false;
        if (mCmpBuffer) {
            unsigned char *vncbuf;
            if (mCmpBuffer->lock(&vncbuf) != 0) {
                LOGE("Failed to lock buffer");
            }
            if (mPixelFormat == GL_RGB565) {
                compare(mWidth / 2, 1, reinterpret_cast<uint32_t *>(vncbuf),
                        reinterpret_cast<uint32_t *>(mRfbScreenInfoPtr->frameBuffer));
            } else if (mPixelFormat == GL_RGBA) {
                compare(mWidth, 0, reinterpret_cast<uint32_t *>(vncbuf),
                        reinterpret_cast<uint32_t *>(mRfbScreenInfoPtr->frameBuffer));
            }
            mCmpBuffer->unlock();
        } else {
            rfbMarkRectAsModified(mRfbScreenInfoPtr, 0, 0, mWidth, mHeight);
        }
        if (DUMP_ENABLED) {
            static int counter = 20;
            if (--counter == 0) {
                counter = 20;
                dumpFrame(mRfbScreenInfoPtr->frameBuffer);
            }
        }
    }
    if (mJavaVM) {
        mJavaVM->DetachCurrentThread();
    }
}
