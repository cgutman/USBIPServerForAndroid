package org.cgutman.usbip.server.protocol.dev;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UsbIpUnlinkUrbReply extends UsbIpDevicePacket {
	public int status;
	
	public UsbIpUnlinkUrbReply(int seqNum, int devId, int dir, int ep) {
		super(UsbIpDevicePacket.USBIP_RET_UNLINK, seqNum, devId, dir, ep);
	}

	protected byte[] serializeInternal() {
		ByteBuffer bb = ByteBuffer.allocate(UsbIpDevicePacket.USBIP_HEADER_SIZE - 20).order(ByteOrder.BIG_ENDIAN);
		
		bb.putInt(status);
		
		return bb.array();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(String.format("Status: 0x%x\n", status));
		return sb.toString();
	}
}
