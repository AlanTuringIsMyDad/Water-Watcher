package com.teamshortcut.waterwatcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.alespero.expandablecardview.ExpandableCardView;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SettingsActivity extends AppCompatActivity {
    //TODO: convert strings in .xml to the strings.xml file
    //TODO: fix app name resource not being discovered/displayed correctly
    private DrawerLayout drawerLayout;

    /*Bluetooth Variables*/
    private ConnectionService connectionService; //The Android service that handles all Bluetooth communications

    @SuppressLint("HandlerLeak") //TODO: remove
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
                    Toast.makeText(getApplicationContext(), "Device was disconnected.", Toast.LENGTH_LONG).show();
                    break;
                case ConnectionService.GATT_SERVICES_DISCOVERED:
                    bundle = msg.getData();
                    ArrayList<String> stringGattServices = bundle.getStringArrayList("GATT_SERVICES_LIST");

                    if (stringGattServices == null || !stringGattServices.contains(ConnectionService.UARTSERVICE_SERVICE_UUID )){ //Sometimes only generic services are initially found
                        //If the required service isn't found, refresh and retry service discovery
                        connectionService.refreshDeviceCache();
                        connectionService.discoverServices();
                    }
                    else {
                        //Sets up notifications for the Accelerometer Data characteristic
                        connectionService.setCharacteristicNotification(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, true);
                        //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                        connectionService.setDescriptorValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    }
                    break;
//                case ConnectionService.GATT_DESCRIPTOR_WRITTEN:
//                    //Sends a simple byte array to tell the micro:bit to start streaming data
//                    connectionService.setCharacteristicValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, new byte[]{1,1});
//                    break;
                case ConnectionService.NOTIFICATION_INDICATION_RECEIVED: //A notification or indication has occurred so some data has been transmitted to the app
                    bundle = msg.getData();
                    serviceUUID = bundle.getString(ConnectionService.BUNDLE_SERVICE_UUID);
                    characteristicUUID = bundle.getString(ConnectionService.BUNDLE_CHARACTERISTIC_UUID);
                    descriptorUUID = bundle.getString(ConnectionService.BUNDLE_DESCRIPTOR_UUID);
                    bytes = bundle.getByteArray(ConnectionService.BUNDLE_VALUE);

                    Log.i("BLE Data Received", serviceUUID);
                    Log.i("BLE Data Received", characteristicUUID);
                    Log.i("BLE Data Received", String.valueOf(bytes));

                    if (characteristicUUID.equals(ConnectionService.UART_RX_CHARACTERISTIC_UUID)){ //If the received data is from the Accelerometer Data characteristic
                       int length = bytes.length;
                       String ascii = "NULL";
                        try { //Convert from a bytearray to a string
                            ascii = new String(bytes,"US-ASCII");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            Log.i("BLE Data Received", "ENCODING ERROR");
                        }
                        Log.i("BLE Data Received", ascii);
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

            if (connectionService.isConnected()){
                //Sets up notifications for the Accelerometer Data characteristic
                connectionService.setCharacteristicNotification(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, true);
                //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                connectionService.setDescriptorValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    //Checks if a string consists of a valid integer
    private boolean isInteger(String string){
        try {
            Integer.parseInt(string);
            return true;
        }
        catch(NumberFormatException e){
            return false;
        }
    }

    //Validates settings before sending them over BLE
    //Returning an empty string indicates success, otherwise it returns the error message to be displayed
    private String validateSettings(String strTimer, String strPeriod, String strSamples, String strX, String strY, String strThreshold) {
        String message = "";

        if (!(isInteger(strTimer) && isInteger(strPeriod) && isInteger(strSamples) && isInteger(strX) && isInteger(strY) && isInteger(strThreshold))) {
            message += "All inputs must be integers!\n";
        }

        //Try/catch statements are used to avoid type conversion errors, and to avoid duplicating the invalid type message that will be returned
        try{
            int timer = Integer.parseInt(strTimer);
            if (!(timer >= 0 && timer <= 1800)){
                message += "Timer Length must be between 0 and 1800 seconds!\n";
            }
        }
        catch(NumberFormatException e){
            Log.i("Invalid input", strTimer);
        }

        try{
            //Valid periods for the micro:bit's accelerometer: 1, 2, 5, 10, 20, 80, 160 and 640 (ms) but micro:bit memory can only handle 10ms minimum
            boolean periodRegex = Pattern.matches("^(10|20|80|160|640)$", strPeriod);
            if(!(periodRegex)){
                message += "Please select a valid Accelerometer Period from the list of approved values.\n";
            }
        }
        catch(Exception e){
            Log.i("Invalid input", strPeriod);
        }

        try{
            int samples = Integer.parseInt(strSamples);
            if (!(samples >= 1 && samples <= 50)){
                message += "Number of Accelerometer Samples must be between 1 and 50!\n";
            }
        }
        catch(NumberFormatException e){
            Log.i("Invalid input", strSamples);
        }

        try{
            int x = Integer.parseInt(strX);
            if (!(x >= -1024 && x <= 1024)){
                message += "The value of X must be between -1024 and 1024.\n";
            }
        }
        catch(NumberFormatException e){
            Log.i("Invalid input", strX);
        }

        try{
            int y = Integer.parseInt(strY);
            if (!(y >= -1024 && y <= 1024)){
                message += "The value of Y must be between -1024 and 1024.\n";
            }
        }
        catch(NumberFormatException e){
            Log.i("Invalid input", strY);
        }

        try{
            int threshold = Integer.parseInt(strThreshold);
            if (!(threshold >= 0 && threshold <= 1448)){
                message += "The Threshold must be between 0 and 1448.\n";
            }
        }
        catch(NumberFormatException e){
            Log.i("Invalid input", strThreshold);
        }

        return message;
    }

    //Returns the settings string that will be sent over BLE
    private String getAndFormatSettings(){
        String settings = null; //Returning null indicates a validation error

        //Get all inputted data
        EditText timerEditText = (EditText) findViewById(R.id.timer_textbox);
        String timer = String.valueOf(timerEditText.getText());

        Spinner periodSpinner = (Spinner) findViewById(R.id.period_spinner);
        String period = periodSpinner.getSelectedItem().toString();

        EditText samplesEditText = (EditText) findViewById(R.id.samples_textbox);
        String samples = String.valueOf(samplesEditText.getText());

        ExpandableCardView card = findViewById(R.id.advanced);

        EditText xEditText = card.findViewById(R.id.x_textbox);
        String x = String.valueOf(xEditText.getText());

        EditText yEditText = card.findViewById(R.id.y_textbox);
        String y = String.valueOf(yEditText.getText());

        EditText thresholdEditText = card.findViewById(R.id.threshold_textbox);
        String threshold = String.valueOf(thresholdEditText.getText());

        String result = validateSettings(timer, period, samples, x, y, threshold);
        if (result == "") { //Validation returned no errors, so construct the settings string
            settings = timer + "," + period + "," + samples + "," + x + "," + y + "," + threshold;
        }
        else{ //Validation returned errors, so display that to the user
            AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert);
            }
            else {
                builder = new AlertDialog.Builder(this);
            }

            //Constructs an Alert Dialog that displays the relevant validation error message(s)
            builder.setTitle("Validation Error").setMessage(result).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //do nothing
                }
            }).setIcon(android.R.drawable.ic_dialog_alert).show();
        }

        return settings;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Sets up toolbar and navigation bar
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.baseline_menu_white_24);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem current = navigationView.getMenu().getItem(2);
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
                        Intent connectionServiceIntent = new Intent(SettingsActivity.this, ConnectionService.class);
                        stopService(connectionServiceIntent);

                        intent = new Intent(SettingsActivity.this, DeviceSelectActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.graph_drawer_item:
                        intent = new Intent(SettingsActivity.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.settings_drawer_item:
                        drawerLayout.closeDrawers();
                        break;
                    case R.id.instructions_drawer_item:
                        intent = new Intent(SettingsActivity.this, InstructionsActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                }

                return true;
            }
        });

        //Start the ConnectionService and BLE communications
        Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
        //ComponentName connectionServiceComponent = startService(connectionServiceIntent);
        bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        final Button button = (Button) findViewById(R.id.settings_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (connectionService.isConnected()){
                    try {
                        if (getAndFormatSettings() != null){
                            String text = getAndFormatSettings() + "\\"; //micro:bit expects the data to be terminated with a backslash
                            Log.i("Data", text);
                            byte[] ascii = text.getBytes("US-ASCII"); //Convert from string to a bytearray to be sent
                            connectionService.setCharacteristicValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_TX_CHARACTERISTIC_UUID, ascii);
                        }
                        else{
                            Snackbar.make(view, "Invalid settings!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                            Log.e("Validation Error", "Invalid settings, error displayed in alert dialog");
                        }
                    }
                    catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    Snackbar.make(view, "No micro:bit connected!", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    Log.e("BLE Connection State", "Attempted to send settings with no available connection");
                }
            }
        });
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        connectionService.setCharacteristicNotification(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, false);
        //connectionService.setDescriptorValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_RX_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        try{
            unbindService(serviceConnection);
        }
        catch (Exception e){

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