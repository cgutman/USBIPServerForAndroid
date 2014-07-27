package org.cgutman.usbip.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

public class XferUtils {

	public static int doBulkTransfer(UsbDeviceConnection devConn, UsbEndpoint endpoint, byte[] buff, int interval) {
		int bytesTransferred = 0;
		while (bytesTransferred < buff.length) {
			byte[] remainingBuffer = new byte[buff.length - bytesTransferred];
			
			if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
				// Copy input data into the new buffer
				System.arraycopy(buff, bytesTransferred, remainingBuffer, 0, remainingBuffer.length);
			}
			
			int res = devConn.bulkTransfer(endpoint, remainingBuffer,
					remainingBuffer.length, interval);
			if (res < 0) {
				// Failed transfer terminates the bulk transfer
				System.err.println("Bulk Xfer failed: "+res);
				return -1;
			}
			
			if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
				// Copy output data into the original buffer
				System.arraycopy(remainingBuffer, 0, buff, bytesTransferred, res);
			}
			
			bytesTransferred += res;
			
			if (res < endpoint.getMaxPacketSize()) {
				// A packet less than the maximum size for this endpoint
				// indicates the transfer has ended
				break;
			}
		}
		
		return bytesTransferred;
	}

	public static int doControlTransfer(UsbDeviceConnection devConn, int requestType,
			int request, int value, int index, byte[] buff, int length, int interval) {
		System.out.printf("SETUP: %x %x %x %x %x\n",
				requestType, request, value, index, length);
		
		int res = devConn.controlTransfer(requestType, request, value,
				index, buff, length, interval);
		if (res < 0) {
			System.err.println("Control Xfer failed: "+res);
		}
		
		return res;
	}
}
