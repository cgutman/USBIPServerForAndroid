package org.cgutman.usbip.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.cgutman.usbip.server.protocol.ProtoDefs;
import org.cgutman.usbip.server.protocol.cli.CommonPacket;
import org.cgutman.usbip.server.protocol.cli.DevListReply;
import org.cgutman.usbip.server.protocol.cli.ImportDeviceReply;
import org.cgutman.usbip.server.protocol.cli.ImportDeviceRequest;
import org.cgutman.usbip.server.protocol.dev.UsbIpDevicePacket;
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb;

public class UsbIpServer {
	public static final int PORT = 3240;
	
	private UsbRequestHandler handler;
	private Thread serverThread;
	private ServerSocket serverSock;
	private ConcurrentHashMap<Socket, Thread> connections = new ConcurrentHashMap<Socket, Thread>();
	
	// Returns true if a device is now attached
	private boolean handleRequest(Socket s) throws IOException {
		CommonPacket inMsg = CommonPacket.read(s.getInputStream());
		CommonPacket outMsg;
		
		if (inMsg == null) {
			s.close();
			return false;
		}
		
		boolean res = false;
		System.out.printf("In code: 0x%x\n", inMsg.code);
		if (inMsg.code == ProtoDefs.OP_REQ_DEVLIST) {
			DevListReply dlReply = new DevListReply(inMsg.version);
			dlReply.devInfoList = handler.getDevices();
			if (dlReply.devInfoList == null) {
				dlReply.status = ProtoDefs.ST_NA;
			}
			outMsg = dlReply;
		}
		else if (inMsg.code == ProtoDefs.OP_REQ_IMPORT) {
			ImportDeviceRequest imReq = (ImportDeviceRequest)inMsg;
			ImportDeviceReply imReply = new ImportDeviceReply(inMsg.version);
			
			res = handler.attachToDevice(s, imReq.busid);
			if (res) {
				imReply.devInfo = handler.getDeviceByBusId(imReq.busid);
				if (imReply.devInfo == null) {
					res = false;
				}
			}
			
			if (res) {
				imReply.status = ProtoDefs.ST_OK;
			}
			else {
				imReply.status = ProtoDefs.ST_NA;
			}
			outMsg = imReply;
		}
		else {
			return false;
		}
		
		System.out.printf("Out code: 0x%x\n", outMsg.code);
		s.getOutputStream().write(outMsg.serialize());
		return res;
	}
	
	private boolean handleDevRequest(Socket s) throws IOException {
		UsbIpDevicePacket inMsg = UsbIpDevicePacket.read(s.getInputStream());
		
		if (inMsg.command == UsbIpDevicePacket.USBIP_CMD_SUBMIT) {
			handler.submitUrbRequest(s, (UsbIpSubmitUrb) inMsg);
		}
		else if (inMsg.command == UsbIpDevicePacket.USBIP_CMD_UNLINK) {
			handler.abortUrbRequest(s, (UsbIpUnlinkUrb) inMsg);
		}
		else {
			return false;
		}
		
		return true;
	}
	
	public void killClient(Socket s) {
		Thread t = connections.remove(s);
		
		try {
			s.close();
		} catch (IOException e) {}
		
		t.interrupt();
		
		try {
			t.join();
		} catch (InterruptedException e) {}
	}
	
	private void handleClient(final Socket s) {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					s.setTcpNoDelay(true);
					s.setKeepAlive(true);
					
					while (!isInterrupted()) {
						if (handleRequest(s)) {
							while (handleDevRequest(s));
						}
					}
				} catch (IOException e) {
					System.out.println("Client disconnected");
				} finally {
					handler.cleanupSocket(s);
					
					try {
						s.close();
					} catch (IOException e) {}
				}
			}
		};

		connections.put(s, t);
		t.start();
	}
	
	public void start(UsbRequestHandler handler) {
		this.handler = handler;
		
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					serverSock = new ServerSocket(PORT);
					System.out.println("Server listening on "+PORT);
					while (!isInterrupted()) {
						handleClient(serverSock.accept());
					}
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}
		};
		
		serverThread = t;
		t.start();
	}
	
	public void stop() {
		if (serverSock != null) {
			try {
				serverSock.close();
			} catch (IOException e) {}
			
			serverSock = null;
		}
		
		if (serverThread != null) {
			serverThread.interrupt();
			
			try {
				serverThread.join();
			} catch (InterruptedException e) {}
			
			serverThread = null;
		}
		
		for (Map.Entry<Socket, Thread> entry : connections.entrySet()) {
			try {
				entry.getKey().close();
			} catch (IOException e) {}
			
			entry.getValue().interrupt();
			
			try {
				entry.getValue().join();
			} catch (InterruptedException e) {}
		}
	}
}
