package org.cgutman.usbip.service;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.cgutman.usbip.config.UsbIpConfig;
import org.cgutman.usbip.server.UsbDeviceInfo;
import org.cgutman.usbip.server.UsbIpServer;
import org.cgutman.usbip.server.UsbRequestHandler;
import org.cgutman.usbip.server.protocol.ProtoDefs;
import org.cgutman.usbip.server.protocol.UsbIpDevice;
import org.cgutman.usbip.server.protocol.UsbIpInterface;
import org.cgutman.usbip.server.protocol.dev.UsbIpDevicePacket;
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb;
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrbReply;
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb;
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrbReply;
import org.cgutman.usbip.usb.UsbControlHelper;
import org.cgutman.usbip.usb.UsbDeviceDescriptor;
import org.cgutman.usbip.usb.XferUtils;
import org.cgutman.usbipserverforandroid.R;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.SparseArray;

import androidx.core.app.NotificationCompat;

public class UsbIpService extends Service implements UsbRequestHandler {
	
	private UsbManager usbManager;
	
	private SparseArray<AttachedDeviceContext> connections;
	private SparseArray<Boolean> permission;
	private HashMap<Socket, AttachedDeviceContext> socketMap;
	private UsbIpServer server;
	private WakeLock cpuWakeLock;
	private WifiLock wifiLock;
	
	private static final boolean DEBUG = false;
	
	private static final int NOTIFICATION_ID = 100;

	private final static String CHANNEL_ID = "serviceInfo";
	
	private static final String ACTION_USB_PERMISSION =
		    "org.cgutman.usbip.USB_PERMISSION";
	private PendingIntent usbPermissionIntent;
	private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				UsbDevice dev = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

				synchronized (dev) {
					permission.put(dev.getDeviceId(), intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false));
					dev.notifyAll();
				}
			}
		}
	};
	
	private void updateNotification() {
		Intent intent = new Intent(this, UsbIpConfig.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		int intentFlags = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			intentFlags |= PendingIntent.FLAG_IMMUTABLE;
		}

		PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, intentFlags);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
				.setSmallIcon(R.drawable.notification_icon)
				.setOngoing(true)
				.setSilent(true)
				.setTicker("USB/IP Server Running")
				.setContentTitle("USB/IP Server Running")
				.setAutoCancel(false)
				.setContentIntent(pendIntent)
				.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
		
		if (connections.size() == 0) {
			builder.setContentText("No devices currently shared");
		}
		else {
			builder.setContentText(String.format("Sharing %d device(s)", connections.size()));
		}
		
		startForeground(NOTIFICATION_ID, builder.build());
	}
	
	@SuppressLint("UseSparseArrays")
	@Override
	public void onCreate() {
		super.onCreate();
		
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);		
		connections = new SparseArray<>();
		permission = new SparseArray<>();
		socketMap = new HashMap<>();

		int intentFlags = 0;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			// This PendingIntent must be mutable to allow the framework to populate EXTRA_DEVICE and EXTRA_PERMISSION_GRANTED.
			intentFlags |= PendingIntent.FLAG_MUTABLE;
		}

		Intent i = new Intent(ACTION_USB_PERMISSION);
		i.setPackage(getPackageName());

		usbPermissionIntent = PendingIntent.getBroadcast(this, 0, i, intentFlags);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED);
		}
		else {
			registerReceiver(usbReceiver, filter);
		}
		
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		
		cpuWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBIPServerForAndroid:Service");
		cpuWakeLock.acquire();
		
		wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "USBIPServerForAndroid:Service");
		wifiLock.acquire();
		
		server = new UsbIpServer();
		server.start(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Service Info", NotificationManager.IMPORTANCE_DEFAULT);
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(channel);
		}
		
		updateNotification();
	}
	
	public void onDestroy() {
		super.onDestroy();
		
		server.stop();
		unregisterReceiver(usbReceiver);
		
		wifiLock.release();
		cpuWakeLock.release();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		// Not currently bindable
		return null;
	}
	
	// Here we're going to enumerate interfaces and endpoints
	// to eliminate possible speeds until we've narrowed it
	// down to only 1 which is our speed real speed. In a typical
	// USB driver, the host controller knows the real speed but
	// we need to derive it without HCI help.
	private final static int FLAG_POSSIBLE_SPEED_LOW = 0x01;
	private final static int FLAG_POSSIBLE_SPEED_FULL = 0x02;
	private final static int FLAG_POSSIBLE_SPEED_HIGH = 0x04;
	private int detectSpeed(UsbDevice dev, UsbDeviceDescriptor devDesc) {
		int possibleSpeeds = FLAG_POSSIBLE_SPEED_LOW |
				FLAG_POSSIBLE_SPEED_FULL |
				FLAG_POSSIBLE_SPEED_HIGH;
		
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			UsbInterface iface = dev.getInterface(i);
			for (int j = 0; j < iface.getEndpointCount(); j++) {
				UsbEndpoint endpoint = iface.getEndpoint(j);
				if ((endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) ||
					(endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC)) {
					// Low speed devices can't implement bulk or iso endpoints
					possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_LOW;
				}
				
				if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_CONTROL) {
					if (endpoint.getMaxPacketSize() > 8) {
						// Low speed devices can't use control transfer sizes larger than 8 bytes
						possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_LOW;
					}
					if (endpoint.getMaxPacketSize() < 64) {
						// High speed devices can't use control transfer sizes smaller than 64 bytes
						possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_HIGH;
					}
				}
				else if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
					if (endpoint.getMaxPacketSize() > 8) {
						// Low speed devices can't use interrupt transfer sizes larger than 8 bytes
						possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_LOW;
					}
					if (endpoint.getMaxPacketSize() > 64) {
						// Full speed devices can't use interrupt transfer sizes larger than 64 bytes
						possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_FULL;
					}
				}
				else if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
					// A bulk endpoint alone can accurately distiniguish between
					// full and high speed devices
					if (endpoint.getMaxPacketSize() == 512) {
						// High speed devices can only use 512 byte bulk transfers
						possibleSpeeds = FLAG_POSSIBLE_SPEED_HIGH;
					}
					else {
						// Otherwise it must be full speed
						possibleSpeeds = FLAG_POSSIBLE_SPEED_FULL;
					}
				}
				else if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
					// If the transfer size is 1024, it must be high speed
					if (endpoint.getMaxPacketSize() == 1024) {
						possibleSpeeds = FLAG_POSSIBLE_SPEED_HIGH;
					}
				}
			}
		}
		
		if (devDesc != null) {
			if (devDesc.bcdUSB < 0x200) {
				// High speed only supported on USB 2.0 or higher
				possibleSpeeds &= ~FLAG_POSSIBLE_SPEED_HIGH;
			}
		}
		
		// Return the lowest speed that we're compatible with
		System.out.printf("Speed heuristics for device %d left us with 0x%x\n",
				dev.getDeviceId(), possibleSpeeds);

		if ((possibleSpeeds & FLAG_POSSIBLE_SPEED_LOW) != 0) {
			return UsbIpDevice.USB_SPEED_LOW;
		}
		else if ((possibleSpeeds & FLAG_POSSIBLE_SPEED_FULL) != 0) {
			return UsbIpDevice.USB_SPEED_FULL;
		}
		else if ((possibleSpeeds & FLAG_POSSIBLE_SPEED_HIGH) != 0) {
			return UsbIpDevice.USB_SPEED_HIGH;
		}
		else {
			// Something went very wrong in speed detection
			return UsbIpDevice.USB_SPEED_UNKNOWN;
		}
	}
	
	private static int deviceIdToBusNum(int deviceId) {
		return deviceId / 1000;
	}
	
	private static int deviceIdToDevNum(int deviceId) {
		return deviceId % 1000;
	}
	
	private static int devIdToDeviceId(int devId) {
		// This is the same algorithm as Android uses
		return ((devId >> 16) & 0xFF) * 1000 + (devId & 0xFF);
	}
	
	private static int busIdToBusNum(String busId) {
		if (busId.indexOf('-') == -1) {
			return -1;
		}
		
		return Integer.parseInt(busId.substring(0, busId.indexOf('-')));
	}
	
	private static int busIdToDevNum(String busId) {
		if (busId.indexOf('-') == -1) {
			return -1;
		}
		
		return Integer.parseInt(busId.substring(busId.indexOf('-')+1));
	}
	
	private static int busIdToDeviceId(String busId) {
		return devIdToDeviceId(((busIdToBusNum(busId) << 16) & 0xFF0000) | busIdToDevNum(busId));
	}

	private UsbDeviceInfo getInfoForDevice(UsbDevice dev, UsbDeviceConnection devConn) {
		UsbDeviceInfo info = new UsbDeviceInfo();
		UsbIpDevice ipDev = new UsbIpDevice();
		
		ipDev.path = dev.getDeviceName();
		ipDev.busnum = deviceIdToBusNum(dev.getDeviceId());
		ipDev.devnum =  deviceIdToDevNum(dev.getDeviceId());
		ipDev.busid = String.format("%d-%d", ipDev.busnum, ipDev.devnum);
		
		ipDev.idVendor = (short) dev.getVendorId();
		ipDev.idProduct = (short) dev.getProductId();
		ipDev.bcdDevice = -1;
		
		ipDev.bDeviceClass = (byte) dev.getDeviceClass();
		ipDev.bDeviceSubClass = (byte) dev.getDeviceSubclass();
		ipDev.bDeviceProtocol = (byte) dev.getDeviceProtocol();
		
		ipDev.bConfigurationValue = 0;
		ipDev.bNumConfigurations = 1;
		
		ipDev.bNumInterfaces = (byte) dev.getInterfaceCount();
		
		info.dev = ipDev;
		info.interfaces = new UsbIpInterface[ipDev.bNumInterfaces];
		
		for (int i = 0; i < ipDev.bNumInterfaces; i++) {
			info.interfaces[i] = new UsbIpInterface();
			UsbInterface iface = dev.getInterface(i);
			
			info.interfaces[i].bInterfaceClass = (byte) iface.getInterfaceClass();
			info.interfaces[i].bInterfaceSubClass = (byte) iface.getInterfaceSubclass();
			info.interfaces[i].bInterfaceProtocol = (byte) iface.getInterfaceProtocol();
		}
		
		AttachedDeviceContext context = connections.get(dev.getDeviceId());
		UsbDeviceDescriptor devDesc = null;
		if (context != null) {
			// Since we're attached already, we can directly query the USB descriptors
			// to fill some information that Android's USB API doesn't expose
			devDesc = UsbControlHelper.readDeviceDescriptor(context.devConn);
			
			ipDev.bcdDevice = devDesc.bcdDevice;
			ipDev.bNumConfigurations = devDesc.bNumConfigurations;
		}
		
		ipDev.speed = detectSpeed(dev, devDesc);
		
		return info;
	}
	
	@Override
	public List<UsbDeviceInfo> getDevices() {
		ArrayList<UsbDeviceInfo> list = new ArrayList<>();
		
		for (UsbDevice dev : usbManager.getDeviceList().values()) {
			AttachedDeviceContext context = connections.get(dev.getDeviceId());
			UsbDeviceConnection devConn = null;
			if (context != null) {
				devConn = context.devConn;
			}
			
			list.add(getInfoForDevice(dev, devConn));
		}
		
		return list;
	}
	
	public static void dumpInterfaces(UsbDevice dev) {
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			System.out.printf("%d - Iface %d (%02x/%02x/%02x)\n",
					i, dev.getInterface(i).getId(),
					dev.getInterface(i).getInterfaceClass(),
					dev.getInterface(i).getInterfaceSubclass(),
					dev.getInterface(i).getInterfaceProtocol());
			
			UsbInterface iface = dev.getInterface(i);
			for (int j = 0; j < iface.getEndpointCount(); j++) {
				System.out.printf("\t%d - Endpoint %d (%x/%x)\n",
						j, iface.getEndpoint(j).getEndpointNumber(),
						iface.getEndpoint(j).getAddress(),
						iface.getEndpoint(j).getAttributes());
			}
		}
	}
	
	private static void sendReply(Socket s, UsbIpSubmitUrbReply reply, int status) {
		reply.status = status;
		try {
			// We need to synchronize to avoid writing on top of ourselves
			synchronized (s) {
				s.getOutputStream().write(reply.serialize());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void sendReply(Socket s, UsbIpUnlinkUrbReply reply, int status) {
		reply.status = status;
		try {
			// We need to synchronize to avoid writing on top of ourselves
			synchronized (s) {
				s.getOutputStream().write(reply.serialize());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// FIXME: This dispatching could use some refactoring so we don't have to pass
	// a million parameters to this guy
	private void dispatchRequest(final AttachedDeviceContext context, final Socket s,
			final UsbEndpoint selectedEndpoint, final ByteBuffer buff, final UsbIpSubmitUrb msg) {
		context.requestPool.submit(new Runnable() {
			@Override
			public void run() {
				UsbIpSubmitUrbReply reply = new UsbIpSubmitUrbReply(msg.seqNum,
						msg.devId, msg.direction, msg.ep);
				
				if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) {
					// We need to store our buffer in the URB reply
					reply.inData = buff.array();
				}
				
				if (selectedEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
					if (DEBUG) {
						System.out.printf("Bulk transfer - %d bytes %s on EP %d\n",
								buff.array().length, msg.direction == UsbIpDevicePacket.USBIP_DIR_IN ? "in" : "out",
										selectedEndpoint.getEndpointNumber());
					}
					
					int res;
					do {
						res = XferUtils.doBulkTransfer(context.devConn, selectedEndpoint, buff.array(), 1000);
						
						if (context.requestPool.isShutdown()) {
							// Bail if the queue is being torn down
							return;
						}
						
						if (!context.activeMessages.contains(msg)) {
							// Somebody cancelled the URB, return without responding
							return;
						}
					} while (res == -110); // ETIMEDOUT
					
					if (DEBUG) {
						System.out.printf("Bulk transfer complete with %d bytes (wanted %d)\n",
								res, msg.transferBufferLength);
					}
					
					if (res < 0) {
						reply.status = res;
					}
					else {
						reply.actualLength = res;
						reply.status = ProtoDefs.ST_OK;
					}

					context.activeMessages.remove(msg);
					sendReply(s, reply, reply.status);
				}
				else if (selectedEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
					if (DEBUG) {
						System.out.printf("Interrupt transfer - %d bytes %s on EP %d\n",
								msg.transferBufferLength, msg.direction == UsbIpDevicePacket.USBIP_DIR_IN ? "in" : "out",
										selectedEndpoint.getEndpointNumber());
					}
					
					int res;
					do {
						res = XferUtils.doInterruptTransfer(context.devConn, selectedEndpoint, buff.array(), 1000);
						
						if (context.requestPool.isShutdown()) {
							// Bail if the queue is being torn down
							return;
						}
						
						if (!context.activeMessages.contains(msg)) {
							// Somebody cancelled the URB, return without responding
							return;
						}
					} while (res == -110); // ETIMEDOUT
					
					if (DEBUG) {
						System.out.printf("Interrupt transfer complete with %d bytes (wanted %d)\n",
								res, msg.transferBufferLength);
					}
					
					if (res < 0) {
						reply.status = res;
					}
					else {
						reply.actualLength = res;
						reply.status = ProtoDefs.ST_OK;
					}
					
					context.activeMessages.remove(msg);
					sendReply(s, reply, reply.status);
				}
				else {
					System.err.println("Unsupported endpoint type: "+selectedEndpoint.getType());
					context.activeMessages.remove(msg);
					server.killClient(s);
				}
			}
		});
	}

	@Override
	public void submitUrbRequest(Socket s, UsbIpSubmitUrb msg) {
		UsbIpSubmitUrbReply reply = new UsbIpSubmitUrbReply(msg.seqNum,
				msg.devId, msg.direction, msg.ep);
		
		int deviceId = devIdToDeviceId(msg.devId);
		
		UsbDevice dev = getDevice(deviceId);
		if (dev == null) {
			// The device is gone, so terminate the client
			server.killClient(s);
			return;
		}
		
		AttachedDeviceContext context = connections.get(deviceId);
		if (context == null) {
			// This should never happen, but kill the connection if it does
			server.killClient(s);
			return;
		}
		
		UsbDeviceConnection devConn = context.devConn;
		
		// Control endpoint is handled with a special case
		if (msg.ep == 0) {
			// This is little endian
			ByteBuffer bb = ByteBuffer.wrap(msg.setup).order(ByteOrder.LITTLE_ENDIAN);
			
			byte requestType = bb.get();
			byte request = bb.get();
			short value = bb.getShort();
			short index = bb.getShort();
			short length = bb.getShort();
			
			if (length != 0) {
				reply.inData = new byte[length];
			}

			// This message is now active
			context.activeMessages.add(msg);
			
			int res;
			
			do {
				res = XferUtils.doControlTransfer(devConn, requestType, request, value, index,
					(requestType & 0x80) != 0 ? reply.inData : msg.outData, length, 1000);
				
				if (context.requestPool.isShutdown()) {
					// Bail if the queue is being torn down
					return;
				}
				
				if (!context.activeMessages.contains(msg)) {
					// Somebody cancelled the URB, return without responding
					return;
				}
			} while (res == -110); // ETIMEDOUT

			if (res < 0) {
				reply.status = res;
			}
			else {
				reply.actualLength = res;
				reply.status = ProtoDefs.ST_OK;
			}
			
			context.activeMessages.remove(msg);
			sendReply(s, reply, reply.status);
			return;
		}
		else {
			// Find the correct endpoint
			UsbEndpoint selectedEndpoint = null;
			for (int i = 0; i < dev.getInterfaceCount(); i++) {
				// Check each interface
				UsbInterface iface = dev.getInterface(i);
				for (int j = 0; j < iface.getEndpointCount(); j++) {
					// Check the endpoint number
					UsbEndpoint endpoint = iface.getEndpoint(j);
					if (msg.ep == endpoint.getEndpointNumber()) {
						// Check the direction
						if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) {
							if (endpoint.getDirection() != UsbConstants.USB_DIR_IN) {
								continue;
							}
						}
						else {
							if (endpoint.getDirection() != UsbConstants.USB_DIR_OUT) {
								continue;
							}
						}
						
						// This the right endpoint
						selectedEndpoint = endpoint;
						break;
					}
				}
				
				// Check if we found the endpoint on the last interface
				if (selectedEndpoint != null) {
					break;
				}
			}
			
			if (selectedEndpoint == null) {
				System.err.println("EP not found: "+msg.ep);
				sendReply(s, reply, ProtoDefs.ST_NA);
				return;
			}
			
			ByteBuffer buff;
			if (msg.direction == UsbIpDevicePacket.USBIP_DIR_IN) {
				// The buffer is allocated by us
				buff = ByteBuffer.allocate(msg.transferBufferLength);
			}
			else {
				// The buffer came in with the request
				buff = ByteBuffer.wrap(msg.outData);
			}
			
			// This message is now active
			context.activeMessages.add(msg);
			
			// Dispatch this request asynchronously
			dispatchRequest(context, s, selectedEndpoint, buff, msg);
		}
	}
	
	private UsbDevice getDevice(int deviceId) {
		for (UsbDevice dev : usbManager.getDeviceList().values()) {
			if (dev.getDeviceId() == deviceId) {
				return dev;
			}
		}
		
		return null;
	}
	
	private UsbDevice getDevice(String busId) {
		return getDevice(busIdToDeviceId(busId));
	}

	@Override
	public UsbDeviceInfo getDeviceByBusId(String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
			return null;
		}
		
		AttachedDeviceContext context = connections.get(dev.getDeviceId());
		UsbDeviceConnection devConn = null;
		if (context != null) {
			devConn = context.devConn;
		}
		
		return getInfoForDevice(dev, devConn);
	}

	@Override
	public boolean attachToDevice(Socket s, String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
			return false;
		}
		
		if (connections.get(dev.getDeviceId()) != null) {
			// Already attached
			return false;
		}
		
		if (!usbManager.hasPermission(dev)) {
			// Try to get permission from the user
			permission.put(dev.getDeviceId(), null);
			usbManager.requestPermission(dev, usbPermissionIntent);
			synchronized (dev) {
				while (permission.get(dev.getDeviceId()) == null) {
					try {
						dev.wait(1000);
					} catch (InterruptedException e) {
						return false;
					}
				}
			}
			
			// User may have rejected this
			if (!permission.get(dev.getDeviceId())) {
				return false;
			}
		}
		
		UsbDeviceConnection devConn = usbManager.openDevice(dev);
		if (devConn == null) {
			return false;
		}
		
		// Claim all interfaces since we don't know which one the client wants
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			if (!devConn.claimInterface(dev.getInterface(i), true)) {
				System.err.println("Unabled to claim interface "+dev.getInterface(i).getId());
			}
		}
		
		// Create a context for this attachment
		AttachedDeviceContext context = new AttachedDeviceContext();
		context.devConn = devConn;
		context.device = dev;
		
		// Count all endpoints on all interfaces
		int endpointCount = 0;
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			endpointCount += dev.getInterface(i).getEndpointCount();
		}
		
		// Use a thread pool with a thread per endpoint
		context.requestPool = new ThreadPoolExecutor(endpointCount, endpointCount,
				Long.MAX_VALUE, TimeUnit.DAYS,
				new LinkedBlockingQueue<>(), new ThreadPoolExecutor.DiscardPolicy());
		
		// Create the active message set
		context.activeMessages = new HashSet<>();
		
		connections.put(dev.getDeviceId(), context);
		socketMap.put(s, context);
		
		updateNotification();
		return true;
	}
	
	private void cleanupDetachedDevice(int deviceId) {
		AttachedDeviceContext context = connections.get(deviceId);
		if (context == null) {
			return;
		}
		
		// Clear the this attachment's context
		connections.remove(deviceId);
		
		// Signal queue death
		context.requestPool.shutdownNow();
		
		// Release our claim to the interfaces
		for (int i = 0; i < context.device.getInterfaceCount(); i++) {
			context.devConn.releaseInterface(context.device.getInterface(i));
		}

		// Close the connection
		context.devConn.close();
		
		// Wait for the queue to die
		try {
			context.requestPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {}

		updateNotification();
	}
	
	@Override
	public void detachFromDevice(Socket s, String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
			return;
		}
		
		cleanupDetachedDevice(dev.getDeviceId());
	}

	@Override
	public void cleanupSocket(Socket s) {
		AttachedDeviceContext context = socketMap.remove(s);
		if (context == null) {
			return;
		}
		
		cleanupDetachedDevice(context.device.getDeviceId());
	}

	@Override
	public void abortUrbRequest(Socket s, UsbIpUnlinkUrb msg) {
		AttachedDeviceContext context = socketMap.get(s);
		if (context == null) {
			return;
		}
		
		UsbIpUnlinkUrbReply reply = new UsbIpUnlinkUrbReply(msg.seqNum, msg.devId, msg.direction, msg.ep);
		
		boolean found = false;
		synchronized (context.activeMessages) {
			for (UsbIpSubmitUrb urbMsg : context.activeMessages) {
				if (msg.seqNumToUnlink == urbMsg.seqNum) {
					context.activeMessages.remove(urbMsg);
					found = true;
					break;
				}
			}
		}
		
		System.out.println("Removed URB? " + (found ? "yes" : "no"));
		sendReply(s, reply,
				found ? UsbIpSubmitUrb.USBIP_STATUS_URB_ABORTED :
					-22); // EINVAL
	}
	
	static class AttachedDeviceContext {
		public UsbDevice device;
		public UsbDeviceConnection devConn;
		public ThreadPoolExecutor requestPool;
		public HashSet<UsbIpSubmitUrb> activeMessages;
	}
}
