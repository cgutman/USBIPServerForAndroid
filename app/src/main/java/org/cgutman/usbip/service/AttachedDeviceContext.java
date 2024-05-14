package org.cgutman.usbip.service;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.util.SparseArray;

import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;

import java.util.HashSet;
import java.util.concurrent.ThreadPoolExecutor;

public class AttachedDeviceContext {
    public UsbDevice device;
    public UsbDeviceConnection devConn;
    public UsbConfiguration activeConfiguration;
    public SparseArray<UsbEndpoint> activeConfigurationEndpointsByNumDir;
    public ThreadPoolExecutor requestPool;
    public HashSet<UsbIpSubmitUrb> activeMessages;
}
