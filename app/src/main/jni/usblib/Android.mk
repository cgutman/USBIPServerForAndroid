# Android.mk for errno
MY_LOCAL_PATH := $(call my-dir)

include $(call all-subdir-makefiles)

LOCAL_PATH := $(MY_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE    := usblib
LOCAL_SRC_FILES := usblib_jni.c

include $(BUILD_SHARED_LIBRARY)
