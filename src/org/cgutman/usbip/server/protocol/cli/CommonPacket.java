package org.cgutman.usbip.server.protocol.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.cgutman.usbip.server.protocol.ProtoDefs;
import org.cgutman.usbip.utils.StreamUtils;

public abstract class CommonPacket {
	public short version;
	public short code;
	public int status;
	
	public CommonPacket(byte[] header) {
		ByteBuffer bb = ByteBuffer.wrap(header);
		
		version = bb.getShort();
		code = bb.getShort();
		status = bb.getInt();
	}
	
	public CommonPacket(short version, short code, int status) {
		this.version = version;
		this.code = code;
		this.status = status;
	}
	
	public static CommonPacket read(InputStream in) throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(8);
		StreamUtils.readAll(in, bb.array());
		
		// We should check the version here, but it seems they like to
		// increment it without actually changing the protocol, so I'm
		// not going to.
		bb.getShort();
		
		CommonPacket pkt;
		short code = bb.getShort();
		switch (code)
		{
		case ProtoDefs.OP_REQ_DEVLIST:
			pkt = new DevListRequest(bb.array());
			break;
		case ProtoDefs.OP_REQ_IMPORT:
			pkt = new ImportDeviceRequest(bb.array());
			((ImportDeviceRequest)pkt).populateInternal(in);
			break;
		default:
			System.err.println("Unsupported code: "+code);
			return null;
		}
		
		return pkt;
	}
	
	protected abstract byte[] serializeInternal();
	
	public byte[] serialize() {
		byte[] internalData = serializeInternal();
		
		int internalLen = internalData == null ? 0 : internalData.length;
		ByteBuffer bb = ByteBuffer.allocate(8 + internalLen);
		
		bb.putShort(version);
		bb.putShort(code);
		bb.putInt(status);
		
		if (internalLen != 0) {
			bb.put(internalData);
		}
		
		return bb.array();
	}
}
