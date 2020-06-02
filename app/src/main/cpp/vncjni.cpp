#include <android/log.h>
#include <jni.h>
#include <string.h>
#include <stdio.h>

#include "log.h"
#include "vncserver.h"

extern "C"
{
	jint JNI_OnLoad(JavaVM *vm, void *reserved)
	{
		JNIEnv *env;
		VncServer::getInstance().setJavaVM(vm);
		if (vm->GetEnv((void **)&env, JNI_VERSION_1_4) != JNI_OK)
		{
			LOGE("Failed to get the environment using GetEnv()");
			return -1;
		}
		return JNI_VERSION_1_4;
	}

	void Java_com_a2k_vncserver_VncJni_init(JNIEnv *env, jobject thiz)
	{
		jclass clazz = env->GetObjectClass(thiz);
		VncServer::getInstance().setupNotificationClb(env,
			(jobject)(env->NewGlobalRef(thiz)),
			(jclass)(env->NewGlobalRef(clazz)));
	}

	JNIEXPORT jstring JNICALL Java_com_a2k_vncserver_VncJni_protoGetVersion(JNIEnv *env, jobject obj)
	{
		return env->NewStringUTF(VncServer::getInstance().getVersion().c_str());
	}

	JNIEXPORT jint JNICALL Java_com_a2k_vncserver_VncJni_startServer(JNIEnv *env, jobject obj,
		jint width, jint height, jint pixelFormat, jboolean fullFrameUpdate)
	{
		return VncServer::getInstance().startServer(width, height, pixelFormat, fullFrameUpdate);
	}

	JNIEXPORT jint JNICALL Java_com_a2k_vncserver_VncJni_stopServer(JNIEnv *env, jobject obj)
	{
		return VncServer::getInstance().stopServer();
	}
}
