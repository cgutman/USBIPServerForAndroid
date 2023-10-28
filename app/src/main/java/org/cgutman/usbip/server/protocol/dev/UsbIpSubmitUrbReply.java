package org.cgutman.usbip.server.protocol.dev;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UsbIpSubmitUrbReply extends UsbIpDevicePacket {
	public int status;
	public int actualLength;
	public int startFrame;
	public int numberOfPackets;
	public int errorCount;
	
	public byte[] inData;
	
	public UsbIpSubmitUrbReply(int seqNum, int devId, int dir, int ep) {
		super(UsbIpDevicePacket.USBIP_RET_SUBMIT, seqNum, devId, dir, ep);
	}

	protected byte[] serializeInternal() {
		int inDataLen = inData == null ? 0 : actualLength;
		ByteBuffer bb = ByteBuffer.allocate((UsbIpDevicePacket.USBIP_HEADER_SIZE - 20) +
				inDataLen).order(ByteOrder.BIG_ENDIAN);
		
		bb.putInt(status);
		bb.putInt(actualLength);
		bb.putInt(startFrame);
		bb.putInt(numberOfPackets);
		bb.putInt(errorCount);
		
		bb.position(UsbIpDevicePacket.USBIP_HEADER_SIZE - 20);
		
		if (inDataLen != 0) {
			bb.put(inData, 0, inDataLen);
		}
		
		return bb.array();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(String.format("Status: 0x%x\n", status));
		sb.append(String.format("Actual length: %d\n", actualLength));
		sb.append(String.format("Start frame: %d\n", startFrame));
		sb.append(String.format("Number Of Packets: %d\n", numberOfPackets));
		sb.append(String.format("Error Count: %d\n", errorCount));
		return sb.toString();
	}
}
