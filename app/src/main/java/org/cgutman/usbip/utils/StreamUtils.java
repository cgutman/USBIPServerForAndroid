package org.cgutman.usbip.utils;

import java.io.IOException;
import java.io.InputStream;

public class StreamUtils {
	public static void readAll(InputStream in, byte[] buffer) throws IOException {
		readAll(in, buffer, 0, buffer.length);
	}
	
	public static void readAll(InputStream in, byte[] buffer, int offset, int length) throws IOException {
		int i = 0;
		while (i < length) {
			int ret = in.read(buffer, offset+i, length-i);
			if (ret <= 0) {
				throw new IOException("Read failed: "+ret);
			}
			
			i += ret;
		}
	}
}
