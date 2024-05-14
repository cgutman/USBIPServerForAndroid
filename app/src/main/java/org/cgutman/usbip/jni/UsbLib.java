package org.cgutman.usbip.jni;

public class UsbLib {
	static {
		System.loadLibrary("usblib");
	}

	public static native int doControlTransfer(int fd, byte requestType, byte request, short value,
											   short index, byte[] data, int length, int timeout);

	public static native int doBulkTransfer(int fd, int endpoint, byte[] data, int timeout);
}
