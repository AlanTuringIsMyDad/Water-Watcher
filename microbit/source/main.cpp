//TODO: adjust sensitivity, may need to reduce sampling rate, change timer functionality...
//...or tweak threshold; sqrt(differences squared) or (xdiff+ydiff)/2 ? Mean seems to be accurate at the moment, check actual threshold numbers that you get out of it)

#include <algorithm> //Used to sort arrays
#include <cmath> //Used for absolute values
#include <map> //Used to store default values
#include <string> //Used with functions/libraries that don't support Managed Strings
#include "MicroBit.h" //The micro:bit library to provide access to micro:bit hardware
#include "MicroBitUARTService.h"
MicroBit uBit; //An instance of the micro:bit class; used to interact with micro:bit components
MicroBitUARTService *uart;

//'off' values for the accelerometer
int x;
int y;

int threshold; //Above this threshold, there are considered to be vibrations. Calculated later in the program

int numSamples; //The number of samples taken in checkVibrations()
int period; //The accelerometer period for the micro:bit

/*The program uses an integer timer variable and uBit.sleep() calls, rather than a more specialised timer like a Ticker.
This is done mainly to conserve memory, as memory on the micro:bit is very limited and needed for other services like BLE.
However, it also serves to ensure fibers or other tasks (such as checkVibrations() or using uBit.display) have time to complete and display correctly.
*/
int timerValue; //The initial value of the timer
int timer; //The current value of the timer

//A map (or dictionary) of default values for different variables
//Used as a last resort if something goes wrong in retrieving/assigning data
std::map<string, int> defaults = {
    { "x", 16 },
    { "y", -16 },
    { "threshold", 64 },
    { "timerValue", 30 },
    { "period", 160 },
    { "numSamples", 5 }
};

//Booleans to track which "Mode" the micro:bit is in
//While it is not in Mode 0, vibration checking will pause to allow either Bluetooth activity or recalibration to occur
int mode = 0; //0: Vibration Checking mode, 1: Connection mode, 2: Settings mode, 3: Calibration mode
bool connected = false; //Tracks if there is currently a BLE connection to the micro:bit

//Returns a given variable's default value (if it has one)
int getDefaultValue(string key){
    int result;
    if (defaults.find(key) != defaults.end()){ //If the variable has a default value...
        result = defaults.find(key)->second; //...return the default value
    }
    else{ //Otherwise, return 0
        uBit.serial.send("\r\nERROR: no default for that key found.");
        result = 0;
    }
    return result;
}

//Stores a value in the micro:bit's (persistent) memory
//More information on how exactly this works can be found at https://lancaster-university.github.io/microbit-docs/ubit/storage/
void storeValue(string key, int data){
    ManagedString managedKey(key.c_str()); //Converts string to a ManagedString that uBit functions take
    KeyValuePair* value = uBit.storage.get(managedKey);
    if(value == NULL){ //If no current value exists, add one
        uBit.storage.put(managedKey, (uint8_t *)&data, sizeof(int));
        uBit.serial.send("\r\nNew value stored in memory.");
    }
    else{ //If there is already an existing value stored, overwrite it
        uBit.storage.remove(managedKey);
        uBit.storage.put(managedKey, (uint8_t *)&data, sizeof(int));
        uBit.serial.send("\r\nValue overwritten in memory.");
        delete value; //Removes the object from (volatile) memory
    }
}

//Copies the value associated with a given key from the micro:bit's (persistent) memory
int copyFromMemory(string key){
    int result;
    ManagedString managedKey(key.c_str()); //Converts string to a ManagedString that uBit functions take
    KeyValuePair* storedValue = uBit.storage.get(managedKey);
        if(storedValue == NULL){ //If there is no current value associated with that key...
            result = getDefaultValue(key); //...resort to the default value
            ManagedString error("ERROR");
            uBit.display.scroll(managedKey+error);
            uBit.serial.send("\r\nResorting to default stored value.");
        }
        else{ //Otherwise, copy the value associated with that key from memory
            memcpy(&result, storedValue->value, sizeof(int));
            delete storedValue;
            uBit.serial.send("\r\nCopied stored value from memory.");
        }
    return result;
}

//USE THIS AFTER VARS HAVE BEEN UPDATED OVER BLE; STORE THE VALUES WHEN RECEIVED OVER BLE, TO UPDATE RUNTIME VARIABLES JUST CALL THIS
//Load previously stored values from memory and assign them to the relevant variables
void initialiseVariables(){
    threshold = copyFromMemory("threshold");
    x = copyFromMemory("x");
    y = copyFromMemory("y");
    timerValue = copyFromMemory("timerValue");
    period = copyFromMemory("period");
    numSamples = copyFromMemory("numSamples");
}

//Uses the accelerometer to check for vibrations
bool checkVibrations(){
    //Calculates the amount of time to sleep between sampling the accelerometer
    int sleepTime = round(500 / numSamples);

    //For improved accuracy, the function samples the accelerometer multiple times at intervals adding up to 500ms in total
    int count = 0; //Keeps track of the number of times the vibrations are above the threshold
    for (int i=0; i<numSamples; i++) {
        //If the absolute value of the difference of the x and y accelerometer values is over the threshold...
        if (sqrt(pow((x - uBit.accelerometer.getX()), 2) + pow((y - uBit.accelerometer.getY()), 2)) > threshold){
            count = count + 1; //...increment count by 1
        }
        else{
            count = count;
        }
        uBit.sleep(sleepTime);
    }
    if (count >= (floor(numSamples/2) + 1)){ //If the majority of samples were positive...
        return true; //there are vibrations.
    }
    else{
        return false;
    }
}

//Function to reset x and y motion values. Toggle is True for resetting the 'on' values, False for resetting the 'off' values
void reset(bool toggle) {
    //Gets x and y values 3 times at 1000ms intervals
    int xList[3];
    int yList[3];

    for (int i=0; i<3; i++) {
        xList[i] = uBit.accelerometer.getX();
        yList[i] = uBit.accelerometer.getY();

        ManagedString xStr(xList[i]);
        ManagedString yStr(yList[i]);

        //Debugging over serial
        uBit.serial.send("\r\nX:");
        uBit.serial.send(xStr);
        uBit.serial.send("\r\nY:");
        uBit.serial.send(yStr);
        uBit.sleep(1000);
    }
    //Sorts the arrays so the median can be taken
    std::sort(std::begin(xList), std::end(xList));
    std::sort(std::begin(yList), std::end(yList));

    if (toggle == false){
        uBit.serial.send("\r\ntoggle=false\r\n");
        //Assigns the median x and y values to the 'off' variables
        x = xList[1];
        y = yList[1];

        //Stores the X and Y "off" values in memory
        storeValue("x", x);
        storeValue("y", y);
    }
    else{
        //WARNING: relies on correct x and y 'off' or 'nothing' values being assigned already!
        uBit.serial.send("\r\ntoggle=true\r\n");

        //Takes the difference of the 'nothing' values and the 'on' values and  assigns the average as the threshold.
        int xdiff = fabs(fabs(x) - fabs(xList[1]));
        int ydiff = fabs(fabs(y) - fabs(yList[1]));
        threshold = (xdiff + ydiff) / 2;
        //threshold = sqrt(pow(fabs(fabs(x) - fabs(xList[1])), 2) + pow(fabs(fabs(y) - fabs(yList[1])), 2));
        
        //Stores the threshold in memory
        storeValue("threshold", threshold);
    }
}

void calibrate(){
    mode = 3; //As we are beginning calibration, we have entered "Calibration Mode"
    uBit.display.scroll("Calibrate");

    //Indicates the 'off' values will be taken next
    MicroBitImage cross("255,0,0,0,255\n0,255,0,255,0\n0,0,255,0,0\n0,255,0,255,0\n255,0,0,0,255\n");
    uBit.display.print(cross);

    //Waits until a button is pressed; the first time, the 'off' values are taken, then the 'on' values.
    int con = 0;
    while (con != 2){
        bool pressed = false;
        while (pressed == false){ //Waits until button A is pressed
            if(uBit.buttonA.isPressed() or uBit.buttonB.isPressed()){
                if (con == 0){
                    pressed = true;
                    reset(false); //Initialise 'off' values
                    con = con + 1;
                    //Indicates the 'on' values will be taken next
                    MicroBitImage tick("0,0,0,0,0\n0,0,0,0,255\n0,0,0,255,0\n255,0,255,0,0\n0,255,0,0,0\n");
                    uBit.display.print(tick);
                }
                else if (con == 1){
                    pressed = true;
                    reset(true); //Initialise 'on' values
                    con = con + 1;
                }
                else{
                    con = con;
                }
            }
        }
    }

    //Debugging over serial
    uBit.serial.send("\r\n'off' X:");
    uBit.serial.send(x);
    uBit.serial.send("\r\n'off' Y:");
    uBit.serial.send(y);
    uBit.serial.send("\r\nThreshold:");
    uBit.serial.send(threshold);
    mode = 0; //Exit "Calibration Mode"
}

//The main code loop
void beginChecking(){
    uBit.serial.send("\r\nBegin Checking");
    timer = timerValue;
    //MicroBitImage with all LEDs on, use to flash an alert when timer reaches 0
    MicroBitImage on("255,255,255,255, 255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n");
    while(mode == 0){ //Loop while the micro:bit is in Vibration checking mode
        if (timer <= 0){ //When the timer has finished counting down...
            while (checkVibrations() == true){ //...flashes all LEDs until vibrations have stopped
                for (int i=0; i<5; i++) {
                    uBit.display.clear();
                    uBit.sleep(100);
                    uBit.display.print(on);
                    uBit.sleep(100);                    
                }
            }
            timer = timerValue; //Resets timer
        }
        else if (checkVibrations() == true){ //If there are vibrations...
            uBit.sleep(500); //500ms + 500ms in checkVibrations() to delay 1 second in total
            timer = timer - 1; //...decrement the timer by 1
        }
        else{ //Checks for false positives
            uBit.sleep(500);
            if (checkVibrations() == false){ //If, after 1 second, there are no vibrations, reset the timer
                timer = timerValue;
            }
            else{
                timer = timer - 1; //Otherwise, there really are vibrations, so decrement the timer by 1
            }
        }
        //Displays the current timer value to the micro:bit LEDs and outputs it over serial
        //NOTE: due to how long the micro:bit LEDs takes to display, for double digit numbers only every other number will actually be displayed
        //This does not affect serial debugging
        uBit.display.printAsync(timer);
        uBit.serial.send(timer);
    }
    //At this point the loop has ended and Connection Mode must have been initiated
    uBit.serial.send("\r\nLoop terminated.");
}

// ManagedString getCurrentSettings(){

// }

void onConnected(MicroBitEvent)
{
    connected = true;
    uBit.display.print("C");
    uBit.sleep(5000); //Delays to allow the micro:bit time to start streaming data after the connection has been made
    mode = 0; //Device has now connected, so exit Connection Mode
    create_fiber(beginChecking); //(re)enters main code loop, where vibrations are continually checked
}

void onDisconnected(MicroBitEvent)
{
    uBit.display.print("D");
    connected = false;
}

//Toggles Connection Mode if either Button A or Button B is pressed on the micro:bit
//In Connection Mode the micro:bit waits for a connection over BLE and starts the Accelerometer Service
void onButtonB(MicroBitEvent)
{
    if (mode == 3){
        //If the micro:bit is already in "Calibration Mode", then do nothing
    }
    else{
        if (mode == 1 or mode == 2){ //If the micro:bit is already in Connection Mode or Settings Mode, exit it and return to checking vibrations
            mode = 0;
            uBit.serial.send("\r\nConnection Mode exited.");
            create_fiber(beginChecking);
        }
        else{
            if (connected){ //Enter Settings Mode
                uBit.display.scroll("Settings");
                mode = 2;
                uBit.display.stopAnimation();
                MicroBitImage arrow("0,0,255,0,0\n0,255,0,0,0\n255,255,255,255,255\n0,255,0,0,0\n0,0,255,0,0\n");
                uBit.display.printAsync(arrow);

                bool con = true;
                while (mode == 2 and con == true) {
                    ManagedString eom("\\");
                    ManagedString msg = uart->readUntil(eom);
                    uBit.display.scroll(msg);
                    con = false;
                }
                MicroBitImage smile("0,255,0,255,0\n0,255,0,255,0\n0,0,0,0,0\n255,0,0,0,255\n0,255,255,255,0\n");
                //if validation: smile, othewise: sad
                uBit.display.print(smile);
                uBit.sleep(1000);
                mode = 0;
                create_fiber(beginChecking);
            }
            else{ //Otherwise, enter Connection Mode
                uBit.display.scroll("BLE");
                mode = 1;
                uBit.serial.send("\r\nConnection Mode initiated.");
                new MicroBitAccelerometerService(*uBit.ble, uBit.accelerometer);
                uart = new MicroBitUARTService(*uBit.ble, 32, 32);
                uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_CONNECTED, onConnected);
                uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_DISCONNECTED, onDisconnected);
            }
        }    
    }
}

void onButtonA(MicroBitEvent)
{
    if (mode == 1){
        //If the micro:bit is already in "Connection Mode" do nothing
        uBit.serial.send("MODE 1");
    }
    else if (mode == 2){ //If the micro:bit is in Settings Mode
        uart->send("SETTINGS");
        uBit.serial.send("UART message sent");
    }
    else{
        if (mode == 3){ //If the micro:bit is already in "Calibration Mode", then exit it
            mode = 0;
            uBit.serial.send("\r\nCalibration Mode exited.");
            create_fiber(beginChecking); //(re)enters main code loop, where vibrations are continually checked
        }
        else{
            uBit.serial.send("\r\nCalibration Mode initiated.");
            uBit.messageBus.ignore(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA); //Ignore the button listener while calibration happens
            uBit.messageBus.ignore(MICROBIT_ID_BUTTON_B, MICROBIT_BUTTON_EVT_CLICK, onButtonB); //Ignore the button listener while calibration happens
            calibrate(); //(re)calibrate the micro:bit's accelerometer values
            uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA); //Re-enables the button listener
            create_fiber(beginChecking); //(re)enters main code loop, where vibrations are continually checked
        }
    }
}

int main() {
    uBit.init(); //Initialise the micro:bit runtime
    uBit.serial.send("\r\nSTART PROGRAM");

    KeyValuePair* firstTime = uBit.storage.get("boot");
    if(firstTime == NULL) //If there is no value, this is the first boot after a flash!
    {
        uBit.serial.send("\r\nFirst boot since flash.");
        //Store a value to indicate that the micro:bit has been booted at least once since the last flash
        int boot = 1;
        uBit.storage.put("boot", (uint8_t *)&boot, sizeof(int));

        //Stores default values in memory; values for x, y and threshold are stored in calibrate()
        storeValue("timerValue", getDefaultValue("timerValue"));
        storeValue("period", getDefaultValue("period"));
        storeValue("numSamples", getDefaultValue("numSamples"));

        calibrate(); //Start calibration
        initialiseVariables();
    }
    else{ //Otherwise, the micro:bit has been booted at least once before and should have previously stored values in memory
        uBit.serial.send("\r\nNot first boot since flash.");
        delete firstTime; //Removes the object from the micro:bit's (volatile) memory
        initialiseVariables();
    }

    //Valid periods: 1, 2, 5, 10, 20, 80, 160 and 640 (ms)
    uBit.accelerometer.setPeriod(period); //Sets the accelerometer sample rate

    //Set up listeners for Button A and Button B; runs the same function regardless of which button is pressed
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA);
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_B, MICROBIT_BUTTON_EVT_CLICK, onButtonB);

    create_fiber(beginChecking); //Enters main code loop, where vibrations are continually checked
    //Releases this fiber, meaning the program enters into the scheduler
    //Here, other fibers can finish running, after which the micro:bit simply enters a power efficient sleep
    release_fiber();
}