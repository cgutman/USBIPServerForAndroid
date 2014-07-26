package org.cgutman.usbip.config;

import org.cgutman.usbip.service.UsbIpService;
import org.cgutman.usbipserverforandroid.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class UsbIpConfig extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_usbip_config);
		
		startService(new Intent(this, UsbIpService.class));
	}
}
