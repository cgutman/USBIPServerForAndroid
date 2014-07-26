package org.cgutman.usbip.server.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UsbIpInterface {
	public byte bInterfaceClass;
	public byte bInterfaceSubClass;
	public byte bInterfaceProtocol;
	
	public static final int WIRE_SIZE = 4;
	
	public byte[] serialize() {
		ByteBuffer bb = ByteBuffer.allocate(WIRE_SIZE).order(ByteOrder.BIG_ENDIAN);
		
		bb.put(bInterfaceClass);
		bb.put(bInterfaceSubClass);
		bb.put(bInterfaceProtocol);
		// Extra alignment padding of 1 byte
		
		return bb.array();
	}
}
