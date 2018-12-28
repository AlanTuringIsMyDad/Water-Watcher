package com.teamshortcut.waterwatcher;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

//TODO: http://www.android-graphview.org/zooming-and-scrolling/
//TODO: Add graph.getViewport().setScrollable(true); but only on disconnect? Otherwise fatal exception occurs
//TODO: Check for BLE object if null? If not null then disable scroll?
//TODO: label x axis as time in seconds and y axis as g-force(?)

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class GraphingActivity extends AppCompatActivity {
    private DrawerLayout drawerLayout;

    /*Graph Variables*/
    //Used to store the data points that will be displayed to the graph
    private LineGraphSeries<DataPoint> xSeries;
    private LineGraphSeries<DataPoint> ySeries;
    private LineGraphSeries<DataPoint> absoluteSeries;

    //Used to store and later access the data points, used when exporting to a CSV file
    private ArrayList<Integer> xList;
    private ArrayList<Integer> yList;
    private ArrayList<Integer> absoluteList;

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

    //Numerical ID, used internally
    private static final int PERMISSION_REQUEST_WRITE_STORAGE = 10;

    private boolean enabled = false; //Tracks if storage permission is granted

    private static final String DIRECTORY_NAME = "/Water-Watcher";
    private static final String BASE_FILENAME = "/waterwatcher-graph-data";

    //Called after a request for an Android permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_WRITE_STORAGE: {
                //If request is cancelled, the result arrays are empty
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission was granted
                    enabled = true;
                } else {
                    //Permission was denied
                    Toast.makeText(getApplicationContext(), getString(R.string.storage_request), Toast.LENGTH_LONG).show();
                    enabled = false;
                }
            }
        }
    }

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
                    Toast.makeText(getApplicationContext(), R.string.device_disconnected, Toast.LENGTH_LONG).show();
                    break;
                case ConnectionService.GATT_SERVICES_DISCOVERED:
                    bundle = msg.getData();
                    ArrayList<String> stringGattServices = bundle.getStringArrayList(ConnectionService.GATT_SERVICES_LIST);

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

            if (connectionService.isConnected()){
                //Sets up notifications for the Accelerometer Data characteristic
                connectionService.setCharacteristicNotification(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, true);
                //GATT Descriptor is used to write to the micro:bit, to enable notifications and tell the device to start streaming data
                connectionService.setDescriptorValueAndWrite(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
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
        try{
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
        catch (Exception e){
            //Likely a null or ArrayIndexOutOfBounds exception, caused by reading data before everything has been correctly initialised; can be safely ignored
            Log.e("Fatal exception caught","Exception in processData");
            e.printStackTrace();
        }

    }

    //Updates the graph with a new set of data points
    private void updateGraph(short x, short y, int absoluteValue) {
        //Start the timer, if it has not already been started
        if (currentTime == 0) {
            timer.scheduleAtFixedRate(timerTask, 0, 1);
        }

        //Add the data to the lists used when later exporting to a CSV file
        xList.add((int) x);
        yList.add((int) y);
        absoluteList.add(absoluteValue);

        //Add the accelerometer received over BLE to the corresponding graph series
        xSeries.appendData(new DataPoint(currentTime, x), true, 1000);
        ySeries.appendData(new DataPoint(currentTime, y), true, 1000);
        absoluteSeries.appendData(new DataPoint(currentTime, absoluteValue), true, 1000);
    }

    //Sets up all graph settings and variables
    private void initialiseGraph() {
        //Initialises the graph series to store the data points
        xSeries = new LineGraphSeries<>();
        ySeries = new LineGraphSeries<>();
        absoluteSeries = new LineGraphSeries<>();

        //Sets the titles of each graph (used in the legend)
        xSeries.setTitle(getString(R.string.graph_plot_title_x));
        ySeries.setTitle(getString(R.string.graph_plot_title_y));
        absoluteSeries.setTitle(getString(R.string.graph_plot_title_absolute_value));

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

    //Recursively returns the next available numbered CSV file for a given filename, so as to not overwrite an existing file
    //For example, if file.csv file1.csv and file2.csv exist, it will return file3.csv
    private File numberUntilNewFile(String filename, int count){
        File file;
        if (count == 0){
            file = new File(filename+".csv");
        }
        else{
            file = new File(filename+count+".csv");
        }

        if (file.exists()){
            return numberUntilNewFile(filename, count+1);
        }
        else{
            return file;
        }
    }

    //Exports the current graph data to a CSV file
    private void exportCSV() throws IOException {
        //If there is data to export, and all lists have the same amount of data (so there has not been an error)
        if (xList != null && yList != null && absoluteList != null && xList.size() == yList.size() && xList.size() == absoluteList.size()){
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){ //If external storage is available to write to
                //Check the directory exists, if not then create it
                File directory = new File(Environment.getExternalStorageDirectory() + DIRECTORY_NAME);
                if (!(directory.exists() && directory.isDirectory())){
                    if(!directory.mkdir()){ //If creating the directory failed
                        Log.d("Exporting CSV", "Tried and failed to create new directory "+DIRECTORY_NAME);
                        Toast.makeText(getApplicationContext(), R.string.file_error, Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                //The file that will be written to
                File file = numberUntilNewFile(Environment.getExternalStorageDirectory().getAbsolutePath() + DIRECTORY_NAME + BASE_FILENAME, 0);
                CSVWriter writer = new CSVWriter(new FileWriter(file), CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.RFC4180_LINE_END);
                writer.writeNext(new String[]{"x", "y", "absolute"}); //Header

                int length = xList.size(); //All lists have the same size
                //Convert ArrayList to Arrays
                Integer[] xArray = (Integer[]) xList.toArray(new Integer[0]);
                Integer[] yArray = (Integer[]) yList.toArray(new Integer[0]);
                Integer[] absoluteArray = (Integer[]) absoluteList.toArray(new Integer[0]);

                for (int i = 0; i < length; i++){ //Add each data point to a new line in the CSV file
                    writer.writeNext(new String[]{Integer.toString(xArray[i]), Integer.toString(yArray[i]), Integer.toString(absoluteArray[i])});
                }

                writer.close(); //End writing the file
                Log.d("Exporting CSV", "CSV file Written to" + String.valueOf(file));
                Toast.makeText(getApplicationContext(), getString(R.string.exported_success, String.valueOf(file)), Toast.LENGTH_LONG).show();
            }
            else{
                Log.d("Exporting CSV", "Could not access external storage.");
                Toast.makeText(getApplicationContext(), R.string.exported_failure, Toast.LENGTH_LONG).show();
            }
        }
        else{
            Log.d("Exporting CSV", "Data is null or has unequal length.");
            Toast.makeText(getApplicationContext(), R.string.no_data_to_export, Toast.LENGTH_LONG).show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Sets up toolbar and navigation bar
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graphing);
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
                Intent intent;

                switch (menuItem.getItemId()){
                    case R.id.device_select_drawer_item:
                        //Device Select activity will be launched, so disconnect from the current device and stop the Connection Service
                        connectionService.disconnect();
                        Intent connectionServiceIntent = new Intent(GraphingActivity.this, ConnectionService.class);
                        stopService(connectionServiceIntent);

                        intent = new Intent(GraphingActivity.this, DeviceSelectActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.graph_drawer_item:
                        drawerLayout.closeDrawers();
                        break;
                    case R.id.settings_drawer_item:
                        intent = new Intent(GraphingActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        finish();
                        break;
                    case R.id.instructions_drawer_item:
                        intent = new Intent(GraphingActivity.this, InstructionsActivity.class);
                        intent.putExtra(InstructionsActivity.PREVIOUS_ACTIVITY_INTENT, InstructionsActivity.GRAPHING_INTENT);
                        startActivity(intent);
                        finish();
                        break;
                }

                return true;
            }
        });

        //Initialises lists, used for exporting graph data to a CSV file
        xList = new ArrayList<Integer>();
        yList = new ArrayList<Integer>();
        absoluteList = new ArrayList<Integer>();

        //Sets up graph that BLE data will be displayed on
        initialiseGraph();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: image and/or csv?

                //If storage permissions are not yet granted, request them
                if (ContextCompat.checkSelfPermission(GraphingActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    //If running Android M or higher, explicitly request storage permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_STORAGE);
                    } else { //Otherwise, notify the user that location permission must be granted for Bluetooth to function correctly
                        Toast.makeText(getApplicationContext(), R.string.old_version_storage_message, Toast.LENGTH_LONG).show();
                    }
                }
                else{
                    enabled = true;
                }

                if (enabled){
                    try {
                        exportCSV();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.d("Exporting CSV", "IO error while trying to export data to CSV file.");
                        Toast.makeText(getApplicationContext(), R.string.file_error, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        //Start the ConnectionService and BLE communications
        Intent connectionServiceIntent = new Intent(this, ConnectionService.class);
        bindService(connectionServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        connectionService.setCharacteristicNotification(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, false);
        connectionService.setDescriptorValueAndWrite(ConnectionService.ACCELEROMETERSERVICE_SERVICE_UUID, ConnectionService.ACCELEROMETERDATA_CHARACTERISTIC_UUID, ConnectionService.CLIENT_CHARACTERISTIC_CONFIG, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
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