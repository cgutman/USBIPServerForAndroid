package org.cgutman.usbip.server;

import java.io.OutputStream;
import java.util.List;

import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;

public interface UsbRequestHandler {
	public List<UsbDeviceInfo> getDevices();
	public UsbDeviceInfo getDeviceByBusId(String busId);
	
	public boolean attachToDevice(String busId);
	public void detachFromDevice(String busId);
	
	public void submitUrbRequest(OutputStream replyOut, UsbIpSubmitUrb msg);
}
