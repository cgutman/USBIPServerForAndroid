package org.cgutman.usbip.server.protocol.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.cgutman.usbip.server.protocol.UsbIpDevice;
import org.cgutman.usbip.utils.StreamUtils;

public class ImportDeviceRequest extends CommonPacket {
	public String busid;
	
	public ImportDeviceRequest(byte[] header) {
		super(header);
	}
	
	public void populateInternal(InputStream in) throws IOException {
		byte[] bb = new byte[UsbIpDevice.BUS_ID_SIZE];
		StreamUtils.readAll(in, bb);
		
		char[] busIdChars = new char[UsbIpDevice.BUS_ID_SIZE];
		int i;
		for (i = 0; i < bb.length; i++) {
			busIdChars[i] = (char) bb[i];
			if (busIdChars[i] == 0) {
				break;
			}
		}
		
		busid = new String(Arrays.copyOf(busIdChars, i));
	}

	@Override
	protected byte[] serializeInternal() {
		throw new UnsupportedOperationException("Serialization not supported");
	}
}
