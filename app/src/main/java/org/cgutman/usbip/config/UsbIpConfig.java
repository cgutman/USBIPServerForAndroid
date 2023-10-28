package org.cgutman.usbip.config;

import org.cgutman.usbip.service.UsbIpService;
import org.cgutman.usbipserverforandroid.R;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class UsbIpConfig extends Activity {
	private Button serviceButton;
	private TextView serviceStatus;
	
	private boolean running;
	
	private void updateStatus() {
		if (running) {
			serviceButton.setText("Stop Service");
			serviceStatus.setText("USB/IP Service Running");
		}
		else {
			serviceButton.setText("Start Service");
			serviceStatus.setText("USB/IP Service Stopped");
		}
	}
	
	// Elegant Stack Overflow solution to querying running services
	private boolean isMyServiceRunning(Class<?> serviceClass) {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (serviceClass.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_usbip_config);

		serviceButton = (Button) findViewById(R.id.serviceButton);
		serviceStatus = (TextView) findViewById(R.id.serviceStatus);
		
		running = isMyServiceRunning(UsbIpService.class);
		
		updateStatus();
		
		serviceButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (running) {
					stopService(new Intent(UsbIpConfig.this, UsbIpService.class));
				}
				else {
					startService(new Intent(UsbIpConfig.this, UsbIpService.class));
				}
				
				running = !running;
				updateStatus();
			}
		});
	}
}
