package org.cgutman.usbip.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import org.cgutman.usbip.server.protocol.ProtoDefs;
import org.cgutman.usbip.server.protocol.cli.CommonPacket;
import org.cgutman.usbip.server.protocol.cli.DevListReply;
import org.cgutman.usbip.server.protocol.cli.ImportDeviceReply;
import org.cgutman.usbip.server.protocol.cli.ImportDeviceRequest;
import org.cgutman.usbip.server.protocol.dev.UsbIpDevicePacket;
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;

public class UsbIpServer {
	public static final int PORT = 3240;
	
	private ArrayList<Thread> threads = new ArrayList<Thread>();
	private UsbRequestHandler handler;
	private ServerSocket serverSock;
	
	// Returns true if a device is now attached
	private boolean handleRequest(InputStream in, OutputStream out) throws IOException {
		CommonPacket inMsg = CommonPacket.read(in);
		CommonPacket outMsg;
		
		boolean res = false;
		System.out.printf("In code: 0x%x\n", inMsg.code);
		if (inMsg.code == ProtoDefs.OP_REQ_DEVLIST) {
			DevListReply dlReply = new DevListReply();
			dlReply.devInfoList = handler.getDevices();
			if (dlReply.devInfoList == null) {
				dlReply.status = ProtoDefs.ST_NA;
			}
			outMsg = dlReply;
		}
		else if (inMsg.code == ProtoDefs.OP_REQ_IMPORT) {
			ImportDeviceRequest imReq = (ImportDeviceRequest)inMsg;
			ImportDeviceReply imReply = new ImportDeviceReply();
			
			res = handler.attachToDevice(imReq.busid);
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
		out.write(outMsg.serialize());
		return res;
	}
	
	private boolean handleDevRequest(InputStream in, OutputStream out) throws IOException {
		UsbIpDevicePacket inMsg = UsbIpDevicePacket.read(in);
		
		//System.out.println(inMsg);
		if (inMsg.command == UsbIpDevicePacket.USBIP_CMD_SUBMIT) {
			handler.submitUrbRequest(out, (UsbIpSubmitUrb) inMsg);
		}
		else {
			return false;
		}
		
		return true;
	}
	
	private void handleClient(final Socket s) {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					// Disable Nagle
					s.setTcpNoDelay(true);
					
					InputStream in = s.getInputStream();
					OutputStream out = s.getOutputStream();
					while (!isInterrupted()) {
						if (handleRequest(in, out)) {
							while (handleDevRequest(in, out));
						}
					}
				} catch (IOException e) {
					System.out.println("Client disconnected");
				} finally {
					try {
						s.close();
					} catch (IOException e) {}
				}
			}
		};
		
		threads.add(t);
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
		threads.add(t);
		t.start();
	}
	
	public void stop() {
		if (serverSock != null) {
			try {
				serverSock.close();
			} catch (IOException e) {}
		}
		
		for (Thread t : threads) {
			t.interrupt();
			
			try {
				t.join();
			} catch (InterruptedException e) {}
		}
	}
}
