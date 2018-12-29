package com.teamshortcut.waterwatcher;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class InstructionsActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;

    /*Bluetooth Variables*/
    private ConnectionService connectionService; //The Android service that handles all Bluetooth communications
    public static String TARGET_ADDRESS; //MAC address of the micro:bit

    /*Intent Constants*/
    public static final String PREVIOUS_ACTIVITY_INTENT = "PREVIOUS_ACTIVITY";
    public static final String GRAPHING_INTENT = "GRAPHING_ACTIVITY_INTENT";
    public static final String SETTINGS_INTENT = "SETTINGS_ACTIVITY_INTENT";

    private int refreshCount = 0; //tracks how many times the cache has been refreshed and BLE services have been (re)discovered

    private Handler messageHandler = new Handler() { //Handles messages from the ConnectionService, and is where BLE activity is handled
        @Override
        public void handleMessage(Message msg){
            Bundle bundle; //The data the message contains
            String serviceUUID = "";
            String characteristicUUID = "";
            String descriptorUUID = "";
            byte[] bytes = null;

            switch (msg.what){
                case ConnectionService.GATT_CONNECTED: //Once a device has connected...
                    connectionService.discoverServices(); //...discover its services
                    break;
                case ConnectionService.GATT_DISCONNECTED:
                    Toast.makeText(getApplicationContext(), R.string.device_disconnected, Toast.LENGTH_LONG).show();
                    break;
                case ConnectionService.GATT_SERVICES_DISCOVERED:
                    bundle = msg.getData();
                    ArrayList<String> stringGattServices = bundle.getStringArrayList(ConnectionService.GATT_SERVICES_LIST);

                    //Sometimes only generic services are initially found
                    if (stringGattServices == null || !stringGattServices.contains(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID) || !stringGattServices.contains(ConnectionService.UARTSERVICE_SERVICE_UUID)){
                        //Only try refreshing 5 times, before warning the user that the required services could not be found
                        if (refreshCount < 5){
                            //If the required services aren't found, refresh and retry service discovery
                            connectionService.refreshDeviceCache();
                            connectionService.discoverServices();
                            refreshCount += 1;
                        }
                        else{
                            AlertDialog.Builder builder;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                builder = new AlertDialog.Builder(InstructionsActivity.this, android.R.style.Theme_Material_Dialog_Alert);
                            }
                            else {
                                builder = new AlertDialog.Builder(InstructionsActivity.this);
                            }

                            //Constructs an Alert Dialog that warns the user that the BLE services could not be found, and asks if they would like to recheck the device
                            builder.setTitle(getString(R.string.microbit_error_title));
                            builder.setMessage(getString(R.string.microbit_error_content));
                            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    //Check for BLE services again
                                    refreshCount = 0;
                                    connectionService.refreshDeviceCache();
                                    connectionService.discoverServices();
                                }
                            });
                            builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Toast.makeText(getApplicationContext(), R.string.device_services_error, Toast.LENGTH_LONG).show();
                                }
                            });
                            builder.setIcon(android.R.drawable.ic_dialog_alert).show();
                        }
                    }
                    break;
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() { //The Android service for the ConnectionService class
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            connectionService = ((ConnectionService.LocalBinder) service).getService();
            connectionService.setActivityHandler(messageHandler); //Assigns messageHandler to handle all messages from this service

            if (!connectionService.isConnected()){
                if (connectionService.connect(TARGET_ADDRESS)){ //Try to connect to the BLE device chosen in the device selection activity
                    Log.d("BLE Connected", "Successfully connected from InstructionsActivity");
                }
                else{
                    Log.e("BLE Failed to connect", "Failed to connect from InstructionsActivity");
                    Toast.makeText(getApplicationContext(), R.string.failed_to_connect, Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Sets up toolbar and navigation bar
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_instructions);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.baseline_menu_white_24);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem current = navigationView.getMenu().getItem(3);
        current.setChecked(true);

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                menuItem.setChecked(true);
                Intent intent;

                switch (menuItem.getItemId()){
                    case R.id.device_select_drawer_item:
                        //Device Select activity will be launched, so disconnect from the current device and stop the Connection Service
                        connectionService.disconnect();
                        Intent connectionServiceIntent = new Intent(InstructionsActivity.this, ConnectionService.class);
                        stopService(connectionServiceIntent);

                        intent = new Intent(InstructionsActivity.this, DeviceSelectActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.graph_drawer_item:
                        intent = new Intent(InstructionsActivity.this, GraphingActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.settings_drawer_item:
                        intent = new Intent(InstructionsActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.instructions_drawer_item:
                        drawerLayout.closeDrawers();
                        break;
                }

                return true;
            }
        });

        //Read intent data from previous activity
        Intent intent = getIntent();
        String address = intent.getStringExtra(ConnectionService.INTENT_DEVICE_ADDRESS);
        Log.i("Intent Extras", "Address: "+address);
        TARGET_ADDRESS = address; //The MAC address of the device to connect to should be the chosen one passed from the device selection activity

        //Scroll to the relevant part of the instructions depending on which view the user just came from
        String previousActivity = intent.getStringExtra(PREVIOUS_ACTIVITY_INTENT);
        final NestedScrollView scrollView = findViewById(R.id.instructions_scroll_view);

        if (previousActivity != null){
            switch (previousActivity){
                case GRAPHING_INTENT:
                    final TextView graphHeader = findViewById(R.id.graphing_header);
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.scrollTo(0, graphHeader.getTop());
                        }
                    });
                    break;
                case SETTINGS_INTENT:
                    final TextView settingsHeader = findViewById(R.id.settings_header);
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.scrollTo(0, settingsHeader.getTop());
                        }
                    });
                    break;
            }
        }

        //Start the ConnectionService and BLE communications
        Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
        bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        try{
            unbindService(serviceConnection);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()){
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}