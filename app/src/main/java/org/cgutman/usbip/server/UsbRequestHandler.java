package org.cgutman.usbip.server;

import java.net.Socket;
import java.util.List;

import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb;

public interface UsbRequestHandler {
	public List<UsbDeviceInfo> getDevices();
	public UsbDeviceInfo getDeviceByBusId(String busId);
	
	public boolean attachToDevice(Socket s, String busId);
	public void detachFromDevice(Socket s, String busId);
	
	public void submitUrbRequest(Socket s, UsbIpSubmitUrb msg);
	public void abortUrbRequest(Socket s, UsbIpUnlinkUrb msg);
	
	public void cleanupSocket(Socket s);
}
