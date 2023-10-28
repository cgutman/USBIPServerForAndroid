package org.cgutman.usbip.server.protocol;

public class ProtoDefs {
	/* public static final short USBIP_VERSION = 0x0111; */
	
	public static final short OP_REQUEST = (short) (0x80 << 8);
	public static final short OP_REPLY = (0x00 << 8);
	
	public static final byte ST_OK = 0x00;
	public static final byte ST_NA = 0x01;
	
	public static final byte OP_IMPORT = 0x03;
	public static final short OP_REQ_IMPORT = (OP_REQUEST | OP_IMPORT);
	public static final short OP_REP_IMPORT = (OP_REPLY | OP_IMPORT);
	
	public static final byte OP_DEVLIST = 0x05;
	public static final short OP_REQ_DEVLIST = (OP_REQUEST | OP_DEVLIST);
	public static final short OP_REP_DEVLIST = (OP_REPLY | OP_DEVLIST);
	
	public static final byte OP_EXPORT = 0x06;
	public static final short OP_REQ_EXPORT = (OP_REQUEST | OP_EXPORT);
	public static final short OP_REP_EXPORT = (OP_REPLY | OP_EXPORT);
	
	public static final byte OP_UNEXPORT = 0x07;
	public static final short OP_REQ_UNEXPORT = (OP_REQUEST | OP_UNEXPORT);
	public static final short OP_REP_UNEXPORT = (OP_REPLY | OP_UNEXPORT);
}
