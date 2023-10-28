package org.cgutman.usbip.server.protocol.cli;

import org.cgutman.usbip.server.UsbDeviceInfo;
import org.cgutman.usbip.server.protocol.ProtoDefs;

public class ImportDeviceReply extends CommonPacket {
	public UsbDeviceInfo devInfo;
	
	public ImportDeviceReply(byte[] header) {
		super(header);
	}
	
	public ImportDeviceReply(short version) {
		super(version, ProtoDefs.OP_REP_IMPORT, ProtoDefs.ST_OK);
	}
	
	@Override
	protected byte[] serializeInternal() {
		if (devInfo == null) {
			return null;
		}
		return devInfo.dev.serialize();
	}
}
