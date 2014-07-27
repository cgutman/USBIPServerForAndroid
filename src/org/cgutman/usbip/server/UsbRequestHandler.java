package org.cgutman.usbip.server;

import java.net.Socket;
import java.util.List;

import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;

public interface UsbRequestHandler {
	public List<UsbDeviceInfo> getDevices();
	public UsbDeviceInfo getDeviceByBusId(String busId);
	
	public boolean attachToDevice(String busId);
	public void detachFromDevice(String busId);
	
	public void submitUrbRequest(Socket s, UsbIpSubmitUrb msg);
}
