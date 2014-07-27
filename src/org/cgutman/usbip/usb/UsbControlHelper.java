package org.cgutman.usbip.usb;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

public class UsbControlHelper {
	
	private static final int GET_DESCRIPTOR_REQUEST_TYPE = 0x80;
	private static final int GET_DESCRIPTOR_REQUEST = 0x06;
	
	private static final int GET_STATUS_REQUEST_TYPE = 0x82;
	private static final int GET_STATUS_REQUEST = 0x00;
	
	private static final int CLEAR_FEATURE_REQUEST_TYPE = 0x02;
	private static final int CLEAR_FEATURE_REQUEST = 0x01;
	
	private static final int FEATURE_VALUE_HALT = 0x00;
	
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
	
	public static boolean isEndpointHalted(UsbDeviceConnection devConn, UsbEndpoint endpoint) {
		byte[] statusBuffer = new byte[2];
		
		int res = XferUtils.doControlTransfer(devConn, GET_STATUS_REQUEST_TYPE,
				GET_STATUS_REQUEST,
				0,
				endpoint != null ? endpoint.getAddress() : 0,
				statusBuffer, statusBuffer.length, 0);
		if (res != statusBuffer.length) {
			return false;
		}
		
		return (statusBuffer[0] & 1) != 0;
	}
	
	public static boolean clearHaltCondition(UsbDeviceConnection devConn, UsbEndpoint endpoint) {
		int res = XferUtils.doControlTransfer(devConn, CLEAR_FEATURE_REQUEST_TYPE,
				CLEAR_FEATURE_REQUEST,
				FEATURE_VALUE_HALT,
				endpoint.getAddress(),
				null, 0, 0);
		if (res < 0) {
			return false;
		}
		
		return true;
	}
}
