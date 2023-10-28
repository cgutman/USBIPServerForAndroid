package org.cgutman.usbip.server.protocol.cli;

import java.nio.ByteBuffer;
import java.util.List;

import org.cgutman.usbip.server.UsbDeviceInfo;
import org.cgutman.usbip.server.protocol.ProtoDefs;

public class DevListReply extends CommonPacket {
	public List<UsbDeviceInfo> devInfoList;
	
	public DevListReply(byte[] header) {
		super(header);
	}
	
	public DevListReply(short version) {
		super(version, ProtoDefs.OP_REP_DEVLIST, ProtoDefs.ST_OK);
	}
	
	@Override
	protected byte[] serializeInternal() {
		int serializedLength = 4;
		
		if (devInfoList != null) {
			for (UsbDeviceInfo info : devInfoList) {
				serializedLength += info.getWireSize();
			}
		}
		
		ByteBuffer bb = ByteBuffer.allocate(serializedLength);
		
		if (devInfoList != null) {
			bb.putInt(devInfoList.size()); 
			
			for (UsbDeviceInfo info : devInfoList) {
				bb.put(info.serialize());
			}
		}
		else {
			bb.putInt(0);
		}
		
		return bb.array();
	}
}
