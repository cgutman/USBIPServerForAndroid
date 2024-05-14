package org.cgutman.usbip.usb;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Build;
import android.util.SparseArray;

import org.cgutman.usbip.service.AttachedDeviceContext;

public class UsbControlHelper {
	
	private static final int GET_DESCRIPTOR_REQUEST_TYPE = 0x80;
	private static final int GET_DESCRIPTOR_REQUEST = 0x06;
	
	private static final int GET_STATUS_REQUEST_TYPE = 0x82;
	private static final int GET_STATUS_REQUEST = 0x00;
	
	private static final int CLEAR_FEATURE_REQUEST_TYPE = 0x02;
	private static final int CLEAR_FEATURE_REQUEST = 0x01;

	private static final int SET_CONFIGURATION_REQUEST_TYPE = 0x00;
	private static final int SET_CONFIGURATION_REQUEST = 0x9;

	private static final int SET_INTERFACE_REQUEST_TYPE = 0x01;
	private static final int SET_INTERFACE_REQUEST = 0xB;

	private static final int FEATURE_VALUE_HALT = 0x00;
	
	private static final int DEVICE_DESCRIPTOR_TYPE = 1;

	public static UsbDeviceDescriptor readDeviceDescriptor(UsbDeviceConnection devConn) {
		byte[] descriptorBuffer = new byte[UsbDeviceDescriptor.DESCRIPTOR_SIZE];
		
		
		int res = XferUtils.doControlTransfer(devConn, GET_DESCRIPTOR_REQUEST_TYPE,
				GET_DESCRIPTOR_REQUEST,
				(DEVICE_DESCRIPTOR_TYPE << 8) | 0x00, // Devices only have 1 descriptor
				0, descriptorBuffer, descriptorBuffer.length, 0);
		if (res != UsbDeviceDescriptor.DESCRIPTOR_SIZE) {
			return null;
		}
		
		return new UsbDeviceDescriptor(descriptorBuffer);
	}
	
	public static boolean isEndpointHalted(UsbDeviceConnection devConn, UsbEndpoint endpoint) {
		byte[] statusBuffer = new byte[2];
		
		int res = XferUtils.doControlTransfer(devConn, GET_STATUS_REQUEST_TYPE,
				GET_STATUS_REQUEST,
				0,
				endpoint != null ? endpoint.getAddress() : 0,
				statusBuffer, statusBuffer.length, 0);
		if (res != statusBuffer.length) {
			return false;
		}
		
		return (statusBuffer[0] & 1) != 0;
	}
	
	public static boolean clearHaltCondition(UsbDeviceConnection devConn, UsbEndpoint endpoint) {
		int res = XferUtils.doControlTransfer(devConn, CLEAR_FEATURE_REQUEST_TYPE,
				CLEAR_FEATURE_REQUEST,
				FEATURE_VALUE_HALT,
				endpoint.getAddress(),
				null, 0, 0);
		if (res < 0) {
			return false;
		}
		
		return true;
	}

	public static boolean handleInternalControlTransfer(AttachedDeviceContext deviceContext, int requestType, int request, int value, int index) {
		// Mask out possible sign expansions
		requestType &= 0xFF;
		request &= 0xFF;
		value &= 0xFFFF;
		index &= 0xFFFF;

		if (requestType == SET_CONFIGURATION_REQUEST_TYPE && request == SET_CONFIGURATION_REQUEST) {
			System.out.println("Handling SET_CONFIGURATION via Android API");

			for (int i = 0; i < deviceContext.device.getConfigurationCount(); i++) {
				UsbConfiguration config = deviceContext.device.getConfiguration(i);
				if (config.getId() == value) {
					// If we have a current config, we need unclaim all interfaces to allow the
					// configuration change to work properly.
					if (deviceContext.activeConfiguration != null) {
						System.out.println("Unclaiming all interfaces from old configuration: "+deviceContext.activeConfiguration.getId());
						for (int j = 0; j < deviceContext.activeConfiguration.getInterfaceCount(); j++) {
							UsbInterface iface = deviceContext.activeConfiguration.getInterface(j);
							deviceContext.devConn.releaseInterface(iface);
						}
					}

					if (!deviceContext.devConn.setConfiguration(config)) {
						// This can happen for certain types of devices where Android itself
						// has set the configuration for us. Let's just hope that whatever the
						// client wanted is also what Android selected :/
						System.err.println("Failed to set configuration! Proceeding anyway!");
					}

					// This is now the active configuration
					deviceContext.activeConfiguration = config;

					// Construct the cache of endpoint mappings
					deviceContext.activeConfigurationEndpointsByNumDir = new SparseArray<>();
					for (int j = 0; j < deviceContext.activeConfiguration.getInterfaceCount(); j++) {
						UsbInterface iface = deviceContext.activeConfiguration.getInterface(j);
						for (int k = 0; k < iface.getEndpointCount(); k++) {
							UsbEndpoint endp = iface.getEndpoint(k);
							deviceContext.activeConfigurationEndpointsByNumDir.put(
									endp.getDirection() | endp.getEndpointNumber(),
									endp);
						}
					}

					System.out.println("Claiming all interfaces from new configuration: "+deviceContext.activeConfiguration.getId());
					for (int j = 0; j < deviceContext.activeConfiguration.getInterfaceCount(); j++) {
						UsbInterface iface = deviceContext.activeConfiguration.getInterface(j);
						if (!deviceContext.devConn.claimInterface(iface, true)) {
							System.err.println("Unable to claim interface: "+iface.getId());
						}
					}

					return true;
				}
			}

			System.err.printf("SET_CONFIGURATION specified invalid configuration: %d\n", value);
		}
		else if (requestType == SET_INTERFACE_REQUEST_TYPE && request == SET_INTERFACE_REQUEST) {
			System.out.println("Handling SET_INTERFACE via Android API");

			if (deviceContext.activeConfiguration != null) {
				for (int i = 0; i < deviceContext.activeConfiguration.getInterfaceCount(); i++) {
					UsbInterface iface = deviceContext.activeConfiguration.getInterface(i);
					if (iface.getId() == index && iface.getAlternateSetting() == value) {
						if (!deviceContext.devConn.setInterface(iface)) {
							System.err.println("Unable to set interface: "+iface.getId());
						}
						return true;
					}
				}

				System.err.printf("SET_INTERFACE specified invalid interface: %d %d\n", index, value);
			}
			else {
				System.err.println("Attempted to use SET_INTERFACE before SET_CONFIGURATION!");
			}
		}

		return false;
	}
}
