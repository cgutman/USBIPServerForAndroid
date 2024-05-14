package org.cgutman.usbip.service;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;

import java.util.HashSet;
import java.util.concurrent.ThreadPoolExecutor;

public class AttachedDeviceContext {
    public UsbDevice device;
    public UsbDeviceConnection devConn;
    public UsbConfiguration activeConfiguration;
    public ThreadPoolExecutor requestPool;
    public HashSet<UsbIpSubmitUrb> activeMessages;
}
