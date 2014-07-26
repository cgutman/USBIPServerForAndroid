package org.cgutman.usbip.server.protocol.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.cgutman.usbip.utils.StreamUtils;

public class UsbIpSubmitUrb extends UsbIpDevicePacket {	
	public int transferFlags;
	public int transferBufferLength;
	public int startFrame;
	public int numberOfPackets;
	public int interval;
	public byte[] setup;
	
	public static final int WIRE_SIZE =
			20 + 8;
	
	public byte[] outData;
	
	public UsbIpSubmitUrb(byte[] header) {
		super(header);
	}
	
	public static UsbIpSubmitUrb read(byte[] header, InputStream in) throws IOException {
		UsbIpSubmitUrb msg = new UsbIpSubmitUrb(header);
		
		byte[] continuationHeader = new byte[WIRE_SIZE];
		StreamUtils.readAll(in, continuationHeader);

		ByteBuffer bb = ByteBuffer.wrap(continuationHeader).order(ByteOrder.BIG_ENDIAN);
		msg.transferFlags = bb.getInt();
		msg.transferBufferLength = bb.getInt();
		msg.startFrame = bb.getInt();
		msg.numberOfPackets = bb.getInt();
		msg.interval = bb.getInt();
		
		msg.setup = new byte[8];
		bb.get(msg.setup);
		
		// Finish reading the remaining bytes of the header as padding
		while (bb.position() < UsbIpDevicePacket.USBIP_HEADER_SIZE - header.length) {
			in.read();
			bb.position(bb.position()+1);
		}
		
		if (msg.direction == UsbIpDevicePacket.USBIP_DIR_OUT) {
			msg.outData = new byte[msg.transferBufferLength];
			StreamUtils.readAll(in, msg.outData);
		}
		
		return msg;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(String.format("Xfer flags: 0x%x\n", transferFlags));
		sb.append(String.format("Xfer length: %d\n", transferBufferLength));
		sb.append(String.format("Start frame: %d\n", startFrame));
		sb.append(String.format("Number Of Packets: %d\n", numberOfPackets));
		sb.append(String.format("Interval: %d\n", interval));
		return sb.toString();
	}

	@Override
	protected byte[] serializeInternal() {
		throw new UnsupportedOperationException("Serializing not supported");
	}
}
