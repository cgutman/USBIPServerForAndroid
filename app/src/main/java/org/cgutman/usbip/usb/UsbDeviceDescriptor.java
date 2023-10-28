package org.cgutman.usbip.usb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UsbDeviceDescriptor {

	public byte bLength;
	public byte bDescriptorType;
	public short bcdUSB;
	public byte bDeviceClass;
	public byte bDeviceSubClass;
	public byte bDeviceProtocol;
	public byte bMaxPacketSize;
	public short idVendor;
	public short idProduct;
	public short bcdDevice;
	public byte iManufacturer;
	public byte iProduct;
	public byte iSerialNumber;
	public byte bNumConfigurations;
	
	public static final int DESCRIPTOR_SIZE = 18;

	public UsbDeviceDescriptor(byte[] data) {
		ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		bLength = bb.get();
		bDescriptorType = bb.get();
		bcdUSB = bb.getShort();
		bDeviceClass = bb.get();
		bDeviceSubClass = bb.get();
		bDeviceProtocol = bb.get();
		bMaxPacketSize = bb.get();
		idVendor = bb.getShort();
		idProduct = bb.getShort();
		bcdDevice = bb.getShort();
		iManufacturer = bb.get();
		iProduct = bb.get();
		iSerialNumber = bb.get();
		bNumConfigurations = bb.get();
	}
}
