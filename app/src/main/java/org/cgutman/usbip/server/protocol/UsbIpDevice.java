package org.cgutman.usbip.server.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class UsbIpDevice {
	public String path;
	public String busid;
	public int busnum;
	public int devnum;
	public int speed;
	
	public short idVendor;
	public short idProduct;
	public short bcdDevice;
	
	public byte bDeviceClass;
	public byte bDeviceSubClass;
	public byte bDeviceProtocol;
	public byte bConfigurationValue;
	public byte bNumConfigurations;
	public byte bNumInterfaces;
	
	public static final int USB_SPEED_UNKNOWN = 0;
	public static final int USB_SPEED_LOW = 1;
	public static final int USB_SPEED_FULL = 2;
	public static final int USB_SPEED_HIGH = 3;
	public static final int USB_SPEED_VARIABLE = 4;
	
	public static final int BUS_ID_SIZE = 32;
	public static final int DEV_PATH_SIZE = 256;
	
	public static final int WIRE_LENGTH =
			BUS_ID_SIZE + DEV_PATH_SIZE + 24;
	
	private static char[] stringToWireChars(String str, int size) {
		char[] strChars = str.toCharArray();
		return Arrays.copyOf(strChars, size);
	}
	
	private static void putChars(ByteBuffer bb, String str, int size) {
		for (char c : stringToWireChars(str, size)) {
			bb.put((byte)c);
		}
	}
	
	public byte[] serialize() {
		ByteBuffer bb = ByteBuffer.allocate(WIRE_LENGTH).order(ByteOrder.BIG_ENDIAN);
		
		putChars(bb, path, DEV_PATH_SIZE);
		putChars(bb, busid, BUS_ID_SIZE);
		bb.putInt(busnum);
		bb.putInt(devnum);
		bb.putInt(speed);
		
		bb.putShort(idVendor);
		bb.putShort(idProduct);
		bb.putShort(bcdDevice);
		
		bb.put(bDeviceClass);
		bb.put(bDeviceSubClass);
		bb.put(bDeviceProtocol);
		bb.put(bConfigurationValue);
		bb.put(bNumConfigurations);
		bb.put(bNumInterfaces);
		
		return bb.array();
	}
}
