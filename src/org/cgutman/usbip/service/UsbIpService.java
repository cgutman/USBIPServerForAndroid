package org.cgutman.usbip.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
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
import org.cgutman.usbip.usb.DescriptorReader;
import org.cgutman.usbip.usb.UsbDeviceDescriptor;
import org.cgutman.usbip.usb.XferUtils;
import org.cgutman.usbipserverforandroid.R;

import android.annotation.SuppressLint;
import android.app.Notification;
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
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.os.IBinder;
import android.util.SparseArray;

public class UsbIpService extends Service implements UsbRequestHandler {
	
	private UsbManager usbManager;
	private SparseArray<AttachedDeviceContext> connections;
	private SparseArray<Boolean> permission;
	private UsbIpServer server;
	
	private static final int NOTIFICATION_ID = 100;
	
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
	
	@SuppressWarnings("deprecation")
	private void updateNotification() {
		Intent intent = new Intent(this, UsbIpConfig.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendIntent = PendingIntent.getActivity(this, 0, intent, 0);
		
		Notification.Builder builder = new Notification.Builder(this);
		builder
		.setTicker("USB/IP Server Running")
		.setContentTitle("USB/IP Server Running")
		.setAutoCancel(false)
		.setOngoing(true)
		.setSmallIcon(R.drawable.notification_icon)
		.setContentIntent(pendIntent);
		
		if (connections.size() == 0) {
			builder.setContentText("No devices currently shared");
		}
		else {
			builder.setContentText(String.format("Sharing %d device(s)", connections.size()));
		}
		
		startForeground(NOTIFICATION_ID, builder.getNotification());
	}
	
	@SuppressLint("UseSparseArrays")
	@Override
	public void onCreate() {
		super.onCreate();
		
		usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		connections = new SparseArray<AttachedDeviceContext>();
		permission = new SparseArray<Boolean>();
		
		usbPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		registerReceiver(usbReceiver, filter);
		
		server = new UsbIpServer();
		server.start(this);
		
		updateNotification();
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

	private UsbDeviceInfo getInfoForDevice(UsbDevice dev) {
		UsbDeviceInfo info = new UsbDeviceInfo();
		UsbIpDevice ipDev = new UsbIpDevice();
		
		ipDev.path = dev.getDeviceName();
		ipDev.busid = String.format("%d", dev.getDeviceId());
		ipDev.busnum = 0;
		ipDev.devnum = dev.getDeviceId();
		
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
			devDesc = DescriptorReader.readDeviceDescriptor(context.devConn);
			
			ipDev.bcdDevice = devDesc.bcdDevice;
			ipDev.bNumConfigurations = devDesc.bNumConfigurations;
		}
		
		ipDev.speed = detectSpeed(dev, devDesc);
		
		return info;
	}
	
	@Override
	public List<UsbDeviceInfo> getDevices() {
		ArrayList<UsbDeviceInfo> list = new ArrayList<UsbDeviceInfo>();
		
		for (UsbDevice dev : usbManager.getDeviceList().values()) {
			list.add(getInfoForDevice(dev));
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
	
	private static void sendReply(OutputStream out, UsbIpSubmitUrbReply reply, int status) {
		reply.status = status;
		try {
			// We need to synchronize to avoid writing on top of ourselves
			synchronized (out) {
				out.write(reply.serialize());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	// FIXME: This dispatching could use some refactoring so we don't have to pass
	// a million parameters to this guy
	private void dispatchRequest(final AttachedDeviceContext context, final OutputStream replyOut,
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
					System.out.printf("Bulk transfer - %d bytes %s on EP %d\n",
							buff.array().length, msg.direction == UsbIpDevicePacket.USBIP_DIR_IN ? "in" : "out",
									selectedEndpoint.getEndpointNumber());
					
					int res = XferUtils.doBulkTransfer(context.devConn,selectedEndpoint, buff.array(), msg.interval);
					
					System.out.printf("Bulk transfer complete with %d bytes (wanted %d)\n",
							res, msg.transferBufferLength);

					if (res < 0) {
						reply.status = ProtoDefs.ST_NA;
					}
					else {
						reply.actualLength = res;
						reply.status = ProtoDefs.ST_OK;
					}
					sendReply(replyOut, reply, reply.status);
				}
				else if (selectedEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
					System.out.printf("Interrupt transfer - %d bytes %s on EP %d\n",
							msg.transferBufferLength, msg.direction == UsbIpDevicePacket.USBIP_DIR_IN ? "in" : "out",
									selectedEndpoint.getEndpointNumber());
										
					UsbRequest req = new UsbRequest();
					req.initialize(context.devConn, selectedEndpoint);
					
					// Create a context for this request so we can identify it
					// when we're done
					UrbContext urbCtxt = new UrbContext();
					urbCtxt.originalMsg = msg;
					urbCtxt.replyMsg = reply;
					urbCtxt.buffer = buff;
					req.setClientData(urbCtxt);
					if (!req.queue(buff, msg.transferBufferLength)) {
						System.err.println("Failed to queue request");
						sendReply(replyOut, reply, ProtoDefs.ST_NA);
						req.close();
						return;
					}
					
					// ---------------- msg is not safe to use below this point -----------
					
					// This can return a different request than what we queued,
					// so we have to lookup the correct reply for this guy
					req = context.devConn.requestWait();
					urbCtxt = (UrbContext) req.getClientData();
					reply = urbCtxt.replyMsg;
					UsbIpSubmitUrb originalMsg = urbCtxt.originalMsg;
					
					
					// On Jelly Bean MR1 (4.2), they exposed the actual transfer length
					// as the byte buffer's position. On previous platforms, we just assume
					// the whole thing went through.
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
						// The byte buffer could have changed so we need to get it from
						// the client data
						reply.actualLength = urbCtxt.buffer.position();
					}
					else {
						reply.actualLength = originalMsg.transferBufferLength;
					}
					
					req.close();
					
					System.out.printf("Interrupt transfer complete with %d bytes (wanted %d)\n",
							reply.actualLength, originalMsg.transferBufferLength);
					if (reply.actualLength == 0) {
						// Request actually failed
						reply.status = ProtoDefs.ST_NA;
					}
					else {
						// LGTM
						reply.status = ProtoDefs.ST_OK;
					}
					
					// Docs are conflicting on whether we need to fill this for non-iso
					// transfers. Nothing in the client driver code seems to use it
					// so I'm not going to...
					reply.startFrame = 0;
					
					sendReply(replyOut, reply, reply.status);
				}
				else {
					System.err.println("Unsupported endpoint type: "+selectedEndpoint.getType());
					sendReply(replyOut, reply, ProtoDefs.ST_NA);
				}
			}
		});
	}

	@Override
	public void submitUrbRequest(OutputStream replyOut, UsbIpSubmitUrb msg) {
		UsbIpSubmitUrbReply reply = new UsbIpSubmitUrbReply(msg.seqNum,
				msg.devId, msg.direction, msg.ep);
		
		UsbDevice dev = getDevice(msg.devId);
		if (dev == null) {
			sendReply(replyOut, reply, ProtoDefs.ST_NA);
			return;
		}
		
		AttachedDeviceContext context = connections.get(msg.devId);
		if (context == null) {
			sendReply(replyOut, reply, ProtoDefs.ST_NA);
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
			
			int res = XferUtils.doControlTransfer(devConn, requestType, request, value, index,
					(requestType & 0x80) != 0 ? reply.inData : msg.outData, length, msg.interval);
			if (res < 0) {
				reply.status = ProtoDefs.ST_NA;
			}
			else {
				reply.actualLength = res;
				reply.status = ProtoDefs.ST_OK;
			}
			
			sendReply(replyOut, reply, reply.status);
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
				sendReply(replyOut, reply, ProtoDefs.ST_NA);
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
			
			// Dispatch this request asynchronously
			dispatchRequest(context, replyOut, selectedEndpoint, buff, msg);
		}
	}
	
	private UsbDevice getDevice(int busId) {
		for (UsbDevice dev : usbManager.getDeviceList().values()) {
			if (dev.getDeviceId() == busId) {
				return dev;
			}
		}
		
		return null;
	}
	
	private UsbDevice getDevice(String busId) {
		int id;
		
		try {
			id = Integer.parseInt(busId);
		} catch (NumberFormatException e) {
			return null;
		}
		
		return getDevice(id);
	}

	@Override
	public UsbDeviceInfo getDeviceByBusId(String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
			return null;
		}
		
		return getInfoForDevice(dev);
	}

	@Override
	public boolean attachToDevice(String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
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
				return false;
			}
		}
		
		// Create a context for this attachment
		AttachedDeviceContext context = new AttachedDeviceContext();
		context.devConn = devConn;
		
		// Count all endpoints on all intefaces
		int endpointCount = 0;
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			endpointCount += dev.getInterface(i).getEndpointCount();
		}
		
		// Use a thread pool with a thread per endpoint
		context.requestPool = new ThreadPoolExecutor(endpointCount, endpointCount,
				Long.MAX_VALUE, TimeUnit.DAYS, 
				new LinkedBlockingQueue<Runnable>(), new ThreadPoolExecutor.DiscardPolicy());
		
		connections.put(dev.getDeviceId(), context);
		
		updateNotification();
		return true;
	}

	@Override
	public void detachFromDevice(String busId) {
		UsbDevice dev = getDevice(busId);
		if (dev == null) {
			return;
		}
		
		AttachedDeviceContext context = connections.get(dev.getDeviceId());
		if (context == null) {
			return;
		}
		
		// Clear the this attachment's context
		connections.remove(dev.getDeviceId());
		
		// Release our claim to the interfaces
		for (int i = 0; i < dev.getInterfaceCount(); i++) {
			context.devConn.releaseInterface(dev.getInterface(i));
		}
		
		// Close the connection
		context.devConn.close();
		
		updateNotification();
	}
	
	class UrbContext {
		public UsbIpSubmitUrb originalMsg;
		public UsbIpSubmitUrbReply replyMsg;
		public ByteBuffer buffer;
	}
	
	class AttachedDeviceContext {
		public UsbDeviceConnection devConn;
		public ThreadPoolExecutor requestPool;
	}
}
