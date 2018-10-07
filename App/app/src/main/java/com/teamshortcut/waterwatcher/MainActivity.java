package com.teamshortcut.waterwatcher;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;

//TODO: http://www.android-graphview.org/zooming-and-scrolling/
//Add graph.getViewport().setScrollable(true); but only on disconnect? Otherwise fatal exception occurs
//Check for BLE object if null? If not null then disable scroll?

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends AppCompatActivity {
    //GRAPH VARIABLES

    //Used to store the data points that will be displayed to the graph
    private LineGraphSeries<DataPoint> xSeries;
    private LineGraphSeries<DataPoint> ySeries;
    private LineGraphSeries<DataPoint> zSeries;
    private LineGraphSeries<DataPoint> absoluteSeries;

    long currentTime = 0; //in milliseconds

    //Increments the current time by 1 millisecond
    Timer timer = new Timer();
    TimerTask timerTask = new TimerTask(){
        @Override
        public void run(){
            currentTime += 1;
        }
    };

    //BLUETOOTH CONSTANTS

    //Should be in the form "0000AAAA-0000-1000-8000-00805f9b34fb" where "AAAA" is to be replaced
    public static String BLE_SIGNATURE_UUID_BASE_START = "0000";
    public static String BLE_SIGNATURE_UUID_BASE_END = "-0000-1000-8000-00805f9b34fb";

    //Each Service has Characteristics, which are used to read/write data
    public static String ACCELEROMETERSERVICE_SERVICE_UUID = "E95D0753251D470AA062FA1922DFA9A8";
    public static String ACCELEROMETERDATA_CHARACTERISTIC_UUID = "E95DCA4B251D470AA062FA1922DFA9A8";
    //public static String ACCELEROMETERPERIOD_CHARACTERISTIC_UUID = "E95DFB24251D470AA062FA1922DFA9A8";
    public static String CLIENT_CHARACTERISTIC_CONFIGURATION_UUID = "2902"; //Used to indicate to the micro:bit that we would like to interact with BLE services

    public static String TARGET_ADDRESS = "C7:D7:2F:2F:2D:8E"; //MAC address of the micro:bit

    //Numerical IDs, used internally
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 10;

    //Bluetooth variables
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice targetDevice;
    BluetoothGatt gattClient; //The GATT Client is what scans and requests data over BLE; in this case, the Android device

    //Defines what to happen upon a BLE scan
    public final BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
            if(bluetoothDevice.getAddress().equals(TARGET_ADDRESS)){ //Scans for the target micro:bit device until it is found
                targetDevice = bluetoothDevice;
            }
        }
    };

    //TODO: combine both formatUUID functions
    public static UUID formatUUIDShort(String uuid){
        String formattedUUID;
        formattedUUID = BLE_SIGNATURE_UUID_BASE_START+uuid+BLE_SIGNATURE_UUID_BASE_END;
        return UUID.fromString(formattedUUID);
    }

    //Used to insert "-"s into the UUID so it is in the format the micro:bit expects
    public static UUID formatUUID(String uuid) {
        String formattedUUID;
        formattedUUID = uuid.substring(0,8) + "-" + uuid.substring(8,12) + "-" + uuid.substring(12,16) + "-" + uuid.substring(16,20) + "-" + uuid.substring(20,32);
        return UUID.fromString(formattedUUID);
    }

    //Used to convert a 2 byte array in Little Endian format to an integer
    public static short convertFromLittleEndianBytes(byte[] bytes) {
        //Checks a 2 byte array has been passed to the function
        if ( bytes == null || bytes.length != 2){
            return 0;
        }
        else{
            //Converts from a byte array in Little Endian format to a short (signed) and returns that value
            return java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort();
        }
    }

    //Accelerometer data is sent from the micro:bit as a byte array and needs to be converted to a signed integer
    private void processData(byte[] data) {
        //BLE data must be split up into the x, y and z values
        byte[] xBytes = new byte[2];
        byte[] yBytes = new byte[2];
        byte[] zBytes = new byte[2];
        //Copies values from data into x/y/zBytes. System.arraycopy() is used for performance+efficiency
        System.arraycopy(data, 0, xBytes, 0, 2);
        System.arraycopy(data, 2, yBytes, 0, 2);
        System.arraycopy(data, 4, zBytes, 0, 2);
        //Data is in bytes in Little Endian form, and needs to be converted to an integer
        short x = convertFromLittleEndianBytes(xBytes);
        short y = convertFromLittleEndianBytes(yBytes);
        short z = convertFromLittleEndianBytes(zBytes);
        int absoluteValue = (int) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
        Log.d("Data", "Accelerometer Data received at time " + currentTime + ": x=" + x + " y=" + y + " z=" + z + "absoluteValue= " + absoluteValue);
        //displayAccelerometerValues(x, y, z);
        updateGraph(x, y, z, absoluteValue);
    }

    //Updates the graph with a new set of data points
    private void updateGraph(short x, short y, short z, int absoluteValue){
        //Start the timer, if it has not already been started
        if (currentTime == 0){
            timer.scheduleAtFixedRate(timerTask,0,1);
        }

        //Add the accelerometer received over BLE to the corresponding series
        xSeries.appendData(new DataPoint(currentTime, x), true, 100);
        ySeries.appendData(new DataPoint(currentTime, y), true, 100);
        zSeries.appendData(new DataPoint(currentTime, z), true, 100);
        absoluteSeries.appendData(new DataPoint(currentTime, absoluteValue), true, 100);
    }


    //Sets up all graph settings and variables
    private void initialiseGraph(){
        //Initialises the graph series to store the data points
        xSeries = new LineGraphSeries<>();
        ySeries = new LineGraphSeries<>();
        zSeries = new LineGraphSeries<>();
        absoluteSeries = new LineGraphSeries<>();

        //Sets the titles of each graph (used in the legend)
        xSeries.setTitle("X");
        ySeries.setTitle("Y");
        zSeries.setTitle("Z");
        absoluteSeries.setTitle("Absolute Value");

        //Sets the colour of each series' lines
        xSeries.setColor(Color.RED);
        ySeries.setColor(Color.GREEN);
        zSeries.setColor(Color.BLUE);
        absoluteSeries.setColor(Color.YELLOW);

        //Links graph object to the correct XML element
        GraphView graph = (GraphView) findViewById(R.id.graph);

        //Assigns a custom LabelRenderer to the graph; this customises the numbered markers along the axes
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    //The x axis should display the time in seconds since the data started streaming
                    //Because the current time is stored in milliseconds, the number that displays should be divided by 1000
                    return String.valueOf(value/1000);
                }
                else {
                    return super.formatLabel(value, isValueX); //The y axis should be displayed as normal
                }
            }
        });

        //The x axis should display 10 markers, and round the numbers to be human readable
        graph.getGridLabelRenderer().setNumHorizontalLabels(10);
        graph.getGridLabelRenderer().setHumanRounding(true);

        //Links the data series with the graph
        graph.addSeries(xSeries);
        graph.addSeries(ySeries);
        graph.addSeries(zSeries);
        graph.addSeries(absoluteSeries);

        //Sets X and Y axis bounds
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-1024);
        graph.getViewport().setMaxY(1774); //As sqrt(1024^2 + 1024^2 + 1024^2) ~= 1774
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(10000);

        //Displays the legend at the top of the graph
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);

        //Sets the titles of each axis
        GridLabelRenderer gridLabel = graph.getGridLabelRenderer();
        gridLabel.setHorizontalAxisTitle("Time (Seconds)");
        gridLabel.setVerticalAxisTitle("g-force (milli-g's)");
    }

    //Displays the accelerometer data to the screen by updating the TextViews
    //Runs on the UI thread rather than directly on the Main thread for performance
    private void displayAccelerometerValues(final short x, final short y, final short z){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //((TextView) MainActivity.this.findViewById(R.id.x)).setText("X = " + x);
                //((TextView) MainActivity.this.findViewById(R.id.y)).setText("Y = " + y);
                //((TextView) MainActivity.this.findViewById(R.id.z)).setText("Z = " + z);
            }
        });
    }

    //Called after a request for an Android permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                //If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission was granted, start the Bluetooth scan
                    bluetoothAdapter.startLeScan(scanCallback);
                } else {
                    //Permission was denied
                    Toast.makeText(getApplicationContext(), R.string.location_request, Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Initialises up BLE objects
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter(); //TODO: handle null exception?

        //Ensures bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //Defines what to do when the device is connected; handles discovering and interacting with BLE services
        final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == STATE_CONNECTED){ //Once a device is connected...
                    gatt.discoverServices(); //...discover its services
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status){
                boolean enabled = true;
                //Gets the Accelerometer Data characteristic from the Accelerometer service...
                //...which is in turn gotten from the GATT Client (the mobile phone)
                BluetoothGattCharacteristic characteristic = gatt.getService(formatUUID(ACCELEROMETERSERVICE_SERVICE_UUID)).getCharacteristic(formatUUID(ACCELEROMETERDATA_CHARACTERISTIC_UUID));
                //Sets up notifications for the Accelerometer Data characteristic. When the characteristic has changed (ie. data has been sent), onCharacteristicChanged() will be called
                gatt.setCharacteristicNotification(characteristic, enabled);

                //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(formatUUIDShort(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }

            @Override
            public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                BluetoothGattCharacteristic characteristic = gatt.getService(formatUUID(ACCELEROMETERSERVICE_SERVICE_UUID)).getCharacteristic(formatUUID(ACCELEROMETERDATA_CHARACTERISTIC_UUID));
                characteristic.setValue(new byte[]{1, 1}); //Sends a simple byte array to tell the micro:bit to start streaming data
                gatt.writeCharacteristic(characteristic);
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                processData(characteristic.getValue()); //When data has been received over BLE, pass it to processData()
            }
        };

        //If running Android M or higher, explicitly request location permission (necessary for Bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }
        else{ //Otherwise, notify the user that location permission must be granted for Bluetooth to function correctly
           Toast.makeText(getApplicationContext(), R.string.old_version_location_message, Toast.LENGTH_LONG).show();
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Sets up graph that BLE data will be displayed on
        initialiseGraph();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (targetDevice == null){ //If targetDevice is null, the micro:bit has not yet been found in the BLE scan
                    Snackbar.make(view, R.string.no_microbit_found, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
                else{
                    Snackbar.make(view, R.string.microbit_found, Snackbar.LENGTH_LONG).setAction("Action", null).show();
                    gattClient = targetDevice.connectGatt(MainActivity.this, true, gattCallback); //Connects the GATT callback to start receiving data; autoConnect is set to True
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}