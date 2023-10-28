package org.cgutman.usbip.server.protocol.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.cgutman.usbip.utils.StreamUtils;

public abstract class UsbIpDevicePacket {
	public static final int USBIP_CMD_SUBMIT = 0x0001;
	public static final int USBIP_CMD_UNLINK = 0x0002;
	public static final int USBIP_RET_SUBMIT = 0x0003;
	public static final int USBIP_RET_UNLINK = 0x0004;
	public static final int USBIP_RESET_DEV = 0xFFFF;
	
	public static final int USBIP_DIR_OUT = 0;
	public static final int USBIP_DIR_IN = 1;
	
	public static final int USBIP_STATUS_ENDPOINT_HALTED = -32;
	public static final int USBIP_STATUS_URB_ABORTED = -54;
	public static final int USBIP_STATUS_DATA_OVERRUN = -75;
	public static final int USBIP_STATUS_URB_TIMED_OUT = -110;
	public static final int USBIP_STATUS_SHORT_TRANSFER = -121;
		
	public static final int USBIP_HEADER_SIZE = 48;
	
	public int command;
	public int seqNum;
	public int devId;
	public int direction;
	public int ep;
	
	public UsbIpDevicePacket(byte[] header) {
		ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
		command = bb.getInt();
		seqNum = bb.getInt();
		devId = bb.getInt();
		direction = bb.getInt();
		ep = bb.getInt();
	}
	
	public UsbIpDevicePacket(int command, int seqNum, int devId, int dir, int ep) {
		this.command = command;
		this.seqNum = seqNum;
		this.devId = devId;
		this.direction = dir;
		this.ep = ep;
	}
	
	public static UsbIpDevicePacket read(InputStream in) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(20);
		
		StreamUtils.readAll(in, bb.array());
		
		int command = bb.getInt();
		switch (command)
		{
		case USBIP_CMD_SUBMIT:
			return UsbIpSubmitUrb.read(bb.array(), in);
		case USBIP_CMD_UNLINK:
			return UsbIpUnlinkUrb.read(bb.array(), in);
		default:
			System.err.println("Unknown command: "+command);
			return null;
		}
	}
	
	protected abstract byte[] serializeInternal();
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Command: 0x%x\n", command));
		sb.append(String.format("Seq: %d\n", seqNum));
		sb.append(String.format("Dev ID: 0x%x\n", devId));
		sb.append(String.format("Direction: %d\n", direction));
		sb.append(String.format("Endpoint: %d\n", ep));
		return sb.toString();
	}
	
	public byte[] serialize() {
		byte[] internalData = serializeInternal();
		
		ByteBuffer bb = ByteBuffer.allocate(20 + internalData.length);
		
		bb.putInt(command);
		bb.putInt(seqNum);
		bb.putInt(devId);
		bb.putInt(direction);
		bb.putInt(ep);
		
		bb.put(internalData);
		
		return bb.array();
	}
}
