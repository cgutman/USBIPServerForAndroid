package org.cgutman.usbip.usb;

import org.cgutman.usbip.jni.UsbLib;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

public class XferUtils {

	public static int doInterruptTransfer(UsbDeviceConnection devConn, UsbEndpoint endpoint, byte[] buff, int timeout) {
		// Interrupt transfers are implemented as one-shot bulk transfers
		int res = UsbLib.doBulkTransfer(devConn.getFileDescriptor(), endpoint.getAddress(), buff, timeout);
		if (res < 0 && res != -110) {
			// Don't print for ETIMEDOUT
			System.err.println("Interrupt Xfer failed: "+res);
		}
		
		return res;
	}
	
	public static int doBulkTransfer(UsbDeviceConnection devConn, UsbEndpoint endpoint, byte[] buff, int timeout) {
		int res = UsbLib.doBulkTransfer(devConn.getFileDescriptor(), endpoint.getAddress(), buff, timeout);
		if (res < 0 && res != -110) {
			// Don't print for ETIMEDOUT
			System.err.println("Bulk Xfer failed: "+res);
		}

		return res;
	}

	public static int doControlTransfer(UsbDeviceConnection devConn, int requestType,
			int request, int value, int index, byte[] buff, int length, int interval) {
		
		// Mask out possible sign expansions
		requestType &= 0xFF;
		request &= 0xFF;
		value &= 0xFFFF;
		index &= 0xFFFF;
		length &= 0xFFFF;
		
		System.out.printf("SETUP: %x %x %x %x %x\n",
				requestType, request, value, index, length);
		
		int res = UsbLib.doControlTransfer(devConn.getFileDescriptor(), (byte)requestType, (byte)request,
				(short)value, (short)index, buff, length, interval);
		if (res < 0 && res != -110) {
			// Don't print for ETIMEDOUT
			System.err.println("Control Xfer failed: "+res);
		}
		
		return res;
	}
}
