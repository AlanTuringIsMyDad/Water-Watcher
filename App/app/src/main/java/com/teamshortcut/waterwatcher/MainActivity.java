package com.teamshortcut.waterwatcher;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

//TODO: http://www.android-graphview.org/zooming-and-scrolling/
//Add graph.getViewport().setScrollable(true); but only on disconnect? Otherwise fatal exception occurs
//Check for BLE object if null? If not null then disable scroll?
//TODO: label x axis as time in seconds and y axis as g-force(?)
//TODO: rename MainActivity, refactor in debug messages and comments too

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MainActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;

    /*Graph Variables*/
    //Used to store the data points that will be displayed to the graph
    private LineGraphSeries<DataPoint> xSeries;
    private LineGraphSeries<DataPoint> ySeries;
    private LineGraphSeries<DataPoint> absoluteSeries;

    long currentTime = 0; //in milliseconds

    //Increments the current time by 1 millisecond
    Timer timer = new Timer();
    TimerTask timerTask = new TimerTask() {
        @Override
        public void run() {
            currentTime += 1;
        }
    };

    /*Bluetooth Variables*/
    private ConnectionService connectionService; //The Android service that handles all Bluetooth communications
    public static String TARGET_ADDRESS; //MAC address of the micro:bit

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
                case ConnectionService.GATT_SERVICES_DISCOVERED:
                    bundle = msg.getData();
                    ArrayList<String> stringGattServices = bundle.getStringArrayList("GATT_SERVICES_LIST");

                    if (stringGattServices == null || !stringGattServices.contains(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID )){ //Sometimes only generic services are initially found
                        //If the required service isn't found, refresh and retry service discovery
                        connectionService.refreshDeviceCache();
                        connectionService.discoverServices();
                    }
                    else {
                        //Sets up notifications for the Accelerometer Data characteristic
                        connectionService.setCharacteristicNotification(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, true);
                        //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                        connectionService.setDescriptorValueAndWrite(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    }
                    break;
                case ConnectionService.GATT_DESCRIPTOR_WRITTEN:
                    //Sends a simple byte array to tell the micro:bit to start streaming data
                    connectionService.setCharacteristicValueAndWrite(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, new byte[]{1,1});
                    break;
                case ConnectionService.NOTIFICATION_INDICATION_RECEIVED: //A notification or indication has occurred so some data has been transmitted to the app
                    bundle = msg.getData();
                    serviceUUID = bundle.getString(ConnectionService.BUNDLE_SERVICE_UUID);
                    characteristicUUID = bundle.getString(ConnectionService.BUNDLE_CHARACTERISTIC_UUID);
                    descriptorUUID = bundle.getString(ConnectionService.BUNDLE_DESCRIPTOR_UUID);
                    bytes = bundle.getByteArray(ConnectionService.BUNDLE_VALUE);

                    if (characteristicUUID.equals(ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID)){ //If the received data is from the Accelerometer Data characteristic
                        processData(bytes); //When data has been received over BLE, pass it to processData()
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
                    Log.d("BLE Connected", "Successfully connected from MainActivity");
                }
                else{
                    Log.e("BLE Failed to connect", "Failed to connect from MainActivity");
                    Toast.makeText(getApplicationContext(), "Failed to connect", Toast.LENGTH_LONG).show();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    //Used to convert a 2 byte array in Little Endian format to an integer
    public static short convertFromLittleEndianBytes(byte[] bytes) {
        //Checks a 2 byte array has been passed to the function
        if (bytes == null || bytes.length != 2) {
            return 0;
        } else {
            //Converts from a byte array in Little Endian format to a short (signed) and returns that value
            return java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort();
        }
    }

    //Accelerometer data is sent from the micro:bit as a byte array and needs to be converted to a signed integer
    private void processData(byte[] data) {
        //BLE data must be split up into the x and y values
        byte[] xBytes = new byte[2];
        byte[] yBytes = new byte[2];
        //Copies values from data into x/y Bytes. System.arraycopy() is used for performance+efficiency
        System.arraycopy(data, 0, xBytes, 0, 2);
        System.arraycopy(data, 2, yBytes, 0, 2);
        //Data is in bytes in Little Endian form, and needs to be converted to an integer
        short x = convertFromLittleEndianBytes(xBytes);
        short y = convertFromLittleEndianBytes(yBytes);
        int absoluteValue = (int) Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
        Log.d("Data", "Accelerometer Data received at time " + currentTime + ": x=" + x + " y=" + y + "absoluteValue= " + absoluteValue);
        //displayAccelerometerValues(x, y);
        updateGraph(x, y, absoluteValue);
    }

    //Updates the graph with a new set of data points
    private void updateGraph(short x, short y, int absoluteValue) {
        //Start the timer, if it has not already been started
        if (currentTime == 0) {
            timer.scheduleAtFixedRate(timerTask, 0, 1);
        }

        //Add the accelerometer received over BLE to the corresponding series
        xSeries.appendData(new DataPoint(currentTime, x), true, 100);
        ySeries.appendData(new DataPoint(currentTime, y), true, 100);
        absoluteSeries.appendData(new DataPoint(currentTime, absoluteValue), true, 100);
    }


    //Sets up all graph settings and variables
    private void initialiseGraph() {
        //Initialises the graph series to store the data points
        xSeries = new LineGraphSeries<>();
        ySeries = new LineGraphSeries<>();
        absoluteSeries = new LineGraphSeries<>();

        //Sets the titles of each graph (used in the legend)
        xSeries.setTitle("X");
        ySeries.setTitle("Y");
        absoluteSeries.setTitle("Absolute Value");

        //Sets the colour of each series' lines
        xSeries.setColor(Color.BLUE);
        ySeries.setColor(Color.GREEN);
        absoluteSeries.setColor(Color.RED);

        //Links graph object to the correct XML element
        GraphView graph = (GraphView) findViewById(R.id.graph);

        //Assigns a custom LabelRenderer to the graph; this customises the numbered markers along the axes
        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                if (isValueX) {
                    //The x axis should display the time in seconds since the data started streaming
                    //Because the current time is stored in milliseconds, the number that displays should be divided by 1000
                    return String.valueOf(value / 1000);
                } else {
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
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.baseline_menu_white_24);

        drawerLayout = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        MenuItem current = navigationView.getMenu().getItem(1);
        current.setChecked(true);

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                menuItem.setChecked(true);

                switch (menuItem.getItemId()){
                    case R.id.device_select_drawer_item:

                        break;
                    case R.id.graph_drawer_item:

                        break;
                    case R.id.settings_drawer_item:
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        break;
                    case R.id.instructions_drawer_item:

                        break;
                }

                return true;
            }
        });

        //Read intent data from previous activity
        Intent intent = getIntent();
        String name = intent.getStringExtra("DEVICENAME");
        String address = intent.getStringExtra("DEVICEADDRESS"); //TODO: change key to constant
        Log.i("Intent Extras", name+address);

        TARGET_ADDRESS = address; //The MAC address of the device to connect to should be the chosen one passed from the device selection activity
        //TARGET_ADDRESS = "C7:D7:2F:2F:2D:8E";

        //Sets up graph that BLE data will be displayed on
        initialiseGraph();

        //Start the ConnectionService and BLE communications
        Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
        //ComponentName connectionServiceComponent = startService(connectionServiceIntent);
        bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);

//        if (!bound){ //If ConnectionService has not already been bound
//            //Start the ConnectionService and BLE communications
//            Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
//            ComponentName connectionServiceComponent = startService(connectionServiceIntent);
//            bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);
//            bound = true;
//        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        connectionService.setCharacteristicNotification(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, false);
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