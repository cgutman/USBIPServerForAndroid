package org.cgutman.usbip.server.protocol.cli;

public class DevListRequest extends CommonPacket {

	public DevListRequest(byte[] header) {
		super(header);
	}

	@Override
	protected byte[] serializeInternal() {
		throw new UnsupportedOperationException("Serialization not supported");
	}

}
