package org.cgutman.usbip.config;

import org.cgutman.usbip.service.UsbIpService;
import org.cgutman.usbipserverforandroid.R;

import android.Manifest;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class UsbIpConfig extends ComponentActivity {
	private Button serviceButton;
	private TextView serviceStatus;
	private TextView serviceReadyText;
	
	private boolean running;

	private ActivityResultLauncher<String> requestPermissionLauncher =
			registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
				// We don't actually care if the permission is granted or not. We will launch the service anyway.
				startService(new Intent(UsbIpConfig.this, UsbIpService.class));
			});
	
	private void updateStatus() {
		if (running) {
			serviceButton.setText("Stop Service");
			serviceStatus.setText("USB/IP Service Running");
			serviceReadyText.setText(R.string.ready_text);
		}
		else {
			serviceButton.setText("Start Service");
			serviceStatus.setText("USB/IP Service Stopped");
			serviceReadyText.setText("");
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

		serviceButton = findViewById(R.id.serviceButton);
		serviceStatus = findViewById(R.id.serviceStatus);
		serviceReadyText = findViewById(R.id.serviceReadyText);
		
		running = isMyServiceRunning(UsbIpService.class);
		
		updateStatus();
		
		serviceButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (running) {
					stopService(new Intent(UsbIpConfig.this, UsbIpService.class));
				}
				else {
					if (ContextCompat.checkSelfPermission(UsbIpConfig.this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
						startService(new Intent(UsbIpConfig.this, UsbIpService.class));
					} else {
						requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
					}
				}
				
				running = !running;
				updateStatus();
			}
		});
	}
}
