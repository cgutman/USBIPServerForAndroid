package org.cgutman.usbip.server.protocol.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.cgutman.usbip.utils.StreamUtils;

public class UsbIpUnlinkUrb extends UsbIpDevicePacket {
	public int seqNumToUnlink;
	
	public static final int WIRE_SIZE = 4;
	
	public UsbIpUnlinkUrb(byte[] header) {
		super(header);
	}
	
	public static UsbIpUnlinkUrb read(byte[] header, InputStream in) throws IOException {
		UsbIpUnlinkUrb msg = new UsbIpUnlinkUrb(header);
		
		byte[] continuationHeader = new byte[WIRE_SIZE];
		StreamUtils.readAll(in, continuationHeader);

		ByteBuffer bb = ByteBuffer.wrap(continuationHeader).order(ByteOrder.BIG_ENDIAN);
		msg.seqNumToUnlink = bb.getInt();
		
		// Finish reading the remaining bytes of the header as padding
		for (int i = 0; i < UsbIpDevicePacket.USBIP_HEADER_SIZE - (header.length + bb.position()); i++) {
			in.read();
		}
		
		return msg;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(super.toString());
		sb.append(String.format("Sequence number to unlink: %d\n", seqNumToUnlink));
		return sb.toString();
	}

	@Override
	protected byte[] serializeInternal() {
		throw new UnsupportedOperationException("Serializing not supported");
	}
}
