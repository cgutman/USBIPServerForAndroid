package org.cgutman.usbip.usb;

import android.hardware.usb.UsbDeviceConnection;

public class DescriptorReader {
	
	private static final int GET_DESCRIPTOR_REQUEST_TYPE = 0x80;
	private static final int GET_DESCRIPTOR_REQUEST = 0x06;
	
	private static final int DEVICE_DESCRIPTOR_TYPE = 1;

	public static UsbDeviceDescriptor readDeviceDescriptor(UsbDeviceConnection devConn) {
		byte[] descriptorBuffer = new byte[UsbDeviceDescriptor.DESCRIPTOR_SIZE];
		
		
		int res = XferUtils.doControlTransfer(devConn, GET_DESCRIPTOR_REQUEST_TYPE,
				GET_DESCRIPTOR_REQUEST,
				(DEVICE_DESCRIPTOR_TYPE << 8) | 0x00, // Devices only have 1 descriptor
				0, descriptorBuffer, descriptorBuffer.length, 0);
		if (res != UsbDeviceDescriptor.DESCRIPTOR_SIZE) {
			return null;
		}
		
		return new UsbDeviceDescriptor(descriptorBuffer);
	}
}
