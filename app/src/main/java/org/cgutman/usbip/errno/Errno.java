package org.cgutman.usbip.errno;

public class Errno {
	static {
		System.loadLibrary("errno");
	}
	
	// This is a really nasty hack to try to grab the error
	// from a USB API request. It may return an undefined result
	// if say the GC runs before this gets called.
	public static native int getErrno();
}
