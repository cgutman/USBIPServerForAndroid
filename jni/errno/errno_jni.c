#include <stdlib.h>
#include <jni.h>

#include <errno.h>

JNIEXPORT jint JNICALL
Java_org_cgutman_usbip_errno_Errno_getErrno(
	JNIEnv *env, jobject this)
{
	return errno;
}
