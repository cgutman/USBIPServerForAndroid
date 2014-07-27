# Android.mk for errno
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := errno
LOCAL_SRC_FILES := errno_jni.c

include $(BUILD_SHARED_LIBRARY)
