package com.teamshortcut.waterwatcher;

import android.bluetooth.BluetoothGattDescriptor;
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
import android.support.annotation.RequiresApi;
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
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.alespero.expandablecardview.ExpandableCardView;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class SettingsActivity extends AppCompatActivity {
    //Views
    private DrawerLayout drawerLayout; //Used for the navigation bar
    //Input views
    EditText timerEditText;
    Spinner periodSpinner;
    EditText samplesEditText;
    ExpandableCardView card;
    EditText xEditText;
    EditText yEditText;
    EditText thresholdEditText;

    //Bluetooth Variables
    private ConnectionService connectionService; //The Android service that handles all Bluetooth communications

    //CONSTANTS
    private static final String EOM_CHAR = "\\"; //The End of Message character the micro:bit program expects in BLE communications is a backslash
    private static final String TIMER_KEY = "TIMER";
    private static final String PERIOD_KEY = "PERIOD";
    private static final String SAMPLES_KEY = "SAMPLES";
    private static final String X_KEY = "X";
    private static final String Y_KEY = "Y";
    private static final String THRESHOLD_KEY = "THRESHOLD";
    private static final String LOG_INVALID_INPUT = "Invalid input";

    //Default values for each setting
    private static HashMap<String, Integer> DEFAULT_VALUES = new HashMap<String, Integer>();
    static {
        {
            DEFAULT_VALUES.put(TIMER_KEY, 30);
            DEFAULT_VALUES.put(PERIOD_KEY, 160);
            DEFAULT_VALUES.put(SAMPLES_KEY, 5);
            DEFAULT_VALUES.put(X_KEY, 16);
            DEFAULT_VALUES.put(Y_KEY, -16);
            DEFAULT_VALUES.put(THRESHOLD_KEY, 64);
        }
    }

    private Handler messageHandler = new Handler() { //Handles messages from the ConnectionService, and is where BLE activity is handled
        @Override
        public void handleMessage(Message msg){ //Handles messages from the ConnectionService, and is where BLE activity is handled
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
                case ConnectionService.NOTIFICATION_INDICATION_RECEIVED: //A notification or indication has occurred so some data has been transmitted to the app
                    bundle = msg.getData();
                    serviceUUID = bundle.getString(ConnectionService.BUNDLE_SERVICE_UUID);
                    characteristicUUID = bundle.getString(ConnectionService.BUNDLE_CHARACTERISTIC_UUID);
                    descriptorUUID = bundle.getString(ConnectionService.BUNDLE_DESCRIPTOR_UUID);
                    bytes = bundle.getByteArray(ConnectionService.BUNDLE_VALUE);

                    if (characteristicUUID.equals(ConnectionService.UART_RX_CHARACTERISTIC_UUID)){ //If the received data is from the Accelerometer Data characteristic
                       String ascii = "NULL";
                        try { //Convert from a bytearray to a string
                            ascii = new String(bytes,"US-ASCII");
                        }
                        catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                            Log.i("BLE Data Received", "ENCODING ERROR");
                        }
                        Log.i("BLE Data Received", ascii);
                        if (ascii.equals("NULL")){ //If encoding failed
                            Toast.makeText(getApplicationContext(), R.string.data_corrupt, Toast.LENGTH_LONG).show();
                        }
                        else{
                            //Strip the EOM character (a "\") from the text
                            ascii = ascii.substring(0, ascii.length() - 1);
                            List<String> newSettings = Arrays.asList(ascii.split(",")); //Split into individual settings
                            updateSettings(newSettings.get(0), newSettings.get(1), newSettings.get(2), newSettings.get(3), newSettings.get(4), newSettings.get(5));
                        }
                    }
                    break;
            }
        }
    };

    //The Android service for the ConnectionService class
    //Enables the Bluetooth connection to persist between activities
    private final ServiceConnection serviceConnection = new ServiceConnection() {
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
            message += getString(R.string.validation_integers);
        }

        //Try/catch statements are used to avoid type conversion errors, and to avoid duplicating the invalid type message that will be returned
        try{
            int timer = Integer.parseInt(strTimer);
            if (!(timer >= 0 && timer <= 1800)){
                message += getString(R.string.validation_timer);
            }
        }
        catch(NumberFormatException e){
            Log.i(LOG_INVALID_INPUT, strTimer);
        }

        try{
            //Valid periods for the micro:bit's accelerometer: 1, 2, 5, 10, 20, 80, 160 and 640 (ms) but we disallow 1ms or micro:bit runs into memory problems and may crash
            boolean periodRegex = Pattern.matches("^(2|5|10|20|80|160|640)$", strPeriod);
            if(!(periodRegex)){
                message += getString(R.string.validation_period);
            }
        }
        catch(Exception e){
            Log.i(LOG_INVALID_INPUT, strPeriod);
        }

        try{
            int samples = Integer.parseInt(strSamples);
            if (!(samples >= 1 && samples <= 50)){
                message += getString(R.string.validation_samples);
            }
        }
        catch(NumberFormatException e){
            Log.i(LOG_INVALID_INPUT, strSamples);
        }

        try{
            int x = Integer.parseInt(strX);
            if (!(x >= -1024 && x <= 1024)){
                message += getString(R.string.validation_x);
            }
        }
        catch(NumberFormatException e){
            Log.i(LOG_INVALID_INPUT, strX);
        }

        try{
            int y = Integer.parseInt(strY);
            if (!(y >= -1024 && y <= 1024)){
                message += getString(R.string.validation_y);
            }
        }
        catch(NumberFormatException e){
            Log.i(LOG_INVALID_INPUT, strY);
        }

        try{
            int threshold = Integer.parseInt(strThreshold);
            if (!(threshold >= 0 && threshold <= 1448)){
                message += getString(R.string.validation_threshold);
            }
        }
        catch(NumberFormatException e){
            Log.i(LOG_INVALID_INPUT, strThreshold);
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
        if (result.equals("")) { //Validation returned no errors, so construct the settings string
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
            builder.setTitle(getString(R.string.validation_error_dialog_title)).setMessage(result).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //do nothing
                }
            }).setIcon(android.R.drawable.ic_dialog_alert).show();
        }

        return settings;
    }

    //Updates the text in each EditText/Spinner, if parameters are null then it resets to their defaults
    private void updateSettings(String newTimer, String newPeriod, String newSamples, String newX, String newY, String newThreshold){
        if (newTimer == null || newPeriod == null || newSamples == null || newX == null || newY == null || newThreshold == null){
            //Reset all input fields to the default value of the corresponding setting
            timerEditText.setText(DEFAULT_VALUES.get(TIMER_KEY).toString());
            periodSpinner.setSelection(((ArrayAdapter)periodSpinner.getAdapter()).getPosition(DEFAULT_VALUES.get(PERIOD_KEY).toString()));
            samplesEditText.setText(DEFAULT_VALUES.get(SAMPLES_KEY).toString());
            xEditText.setText(DEFAULT_VALUES.get(X_KEY).toString());
            yEditText.setText(DEFAULT_VALUES.get(Y_KEY).toString());
            thresholdEditText.setText(DEFAULT_VALUES.get(THRESHOLD_KEY).toString());
        }
        else{
            //Update all input fields with the received settings
            timerEditText.setText(newTimer);
            periodSpinner.setSelection(((ArrayAdapter)periodSpinner.getAdapter()).getPosition(newPeriod));
            samplesEditText.setText(newSamples);
            xEditText.setText(newX);
            yEditText.setText(newY);
            thresholdEditText.setText(newThreshold);
        }
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

        //Initialise variables input views
        timerEditText = (EditText) findViewById(R.id.timer_textbox);
        periodSpinner = (Spinner) findViewById(R.id.period_spinner);
        samplesEditText = (EditText) findViewById(R.id.samples_textbox);
        card = findViewById(R.id.advanced);
        xEditText = card.findViewById(R.id.x_textbox);
        yEditText = card.findViewById(R.id.y_textbox);
        thresholdEditText = card.findViewById(R.id.threshold_textbox);

        //Set hints to the default values of each setting (ignore the Accelerometer Period as a Spinner has no hint attribute)
        timerEditText.setHint(DEFAULT_VALUES.get(TIMER_KEY).toString());
        samplesEditText.setHint(DEFAULT_VALUES.get(SAMPLES_KEY).toString());
        xEditText.setHint(DEFAULT_VALUES.get(X_KEY).toString());
        yEditText.setHint(DEFAULT_VALUES.get(Y_KEY).toString());
        thresholdEditText.setHint(DEFAULT_VALUES.get(THRESHOLD_KEY).toString());

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
                        intent = new Intent(SettingsActivity.this, GraphingActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.settings_drawer_item:
                        drawerLayout.closeDrawers();
                        break;
                    case R.id.instructions_drawer_item:
                        intent = new Intent(SettingsActivity.this, InstructionsActivity.class);
                        intent.putExtra(InstructionsActivity.PREVIOUS_ACTIVITY_INTENT, InstructionsActivity.SETTINGS_INTENT);
                        startActivity(intent);
                        finish();
                        break;
                }

                return true;
            }
        });

        //Start the ConnectionService and BLE communications
        final Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
        //ComponentName connectionServiceComponent = startService(connectionServiceIntent);
        bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        final Button sendButton = (Button) findViewById(R.id.settings_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if (connectionService.isConnected()){
                    try {
                        if (getAndFormatSettings() != null){
                            String text = getAndFormatSettings() + EOM_CHAR; //micro:bit expects the data to be terminated with a backslash
                            Log.i("Data", text);
                            byte[] ascii = text.getBytes("US-ASCII"); //Convert from string to a bytearray to be sent
                            connectionService.setCharacteristicValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_TX_CHARACTERISTIC_UUID, ascii);
                        }
                        else{
                            Snackbar.make(view, R.string.invalid_settings, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                            Log.e("Validation Error", "Invalid settings, error displayed in alert dialog");
                        }
                    }
                    catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    Snackbar.make(view, R.string.no_microbit_connected, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    Log.e("BLE Connection State", "Attempted to send settings with no available connection");
                }
            }
        });

        final Button cancelButton = (Button) findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View view) {
                if (connectionService.isConnected()){
                    try {
                        String text = "CANCEL"+EOM_CHAR;
                        byte[] ascii = text.getBytes("US-ASCII");
                        connectionService.setCharacteristicValueAndWrite(ConnectionService.UARTSERVICE_SERVICE_UUID, ConnectionService.UART_TX_CHARACTERISTIC_UUID, ascii);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    Snackbar.make(view, R.string.no_microbit_connected, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    Log.e("BLE Connection State", "Attempted to send settings with no available connection");
                }
            }
        });

        final Button resetButton = (Button) findViewById(R.id.reset_button);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Passing updateSettings null parameters will reset all input values to default
                updateSettings(null, null, null, null, null, null);
            }
        });

        //Updates the height of all buttons so as to be uniform
        //Run directly before onDraw()
        ViewTreeObserver viewTreeObserver = resetButton.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    resetButton.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    //Gets the largest height from all buttons, and sets height of all buttons to that
                    ArrayList<Integer> buttonHeights = new ArrayList<Integer>();
                    buttonHeights.add(sendButton.getMeasuredHeight());
                    buttonHeights.add(cancelButton.getMeasuredHeight());
                    buttonHeights.add(resetButton.getMeasuredHeight());
                    sendButton.setHeight(Collections.max(buttonHeights));
                    cancelButton.setHeight(Collections.max(buttonHeights));
                    resetButton.setHeight(Collections.max(buttonHeights));
                }
            });
        }
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