//TODO: adjust sensitivity, may need to reduce sampling rate, change timer functionality...
//...or tweak threshold; sqrt(differences squared) or (xdiff+ydiff)/2 ? Mean seems to be accurate at the moment, check actual threshold numbers that you get out of it)

#include <algorithm> //Used to sort arrays
#include <cmath> //Used for absolute values
#include "MicroBit.h" //The micro:bit library to provide access to micro:bit hardware
MicroBit uBit; //An instance of the micro:bit class; used to interact with micro:bit components

//'off' values for the accelerometer
int x;
int y;

int threshold; //Above this threshold, there are considered to be vibrations. Calculated later in the program

/*The program uses an integer timer variable and uBit.sleep() calls, rather than a more specialised timer like a Ticker.
This is done mainly to conserve memory, as memory on the micro:bit is very limited and needed for other services like BLE.
However, it also serves to ensure fibers or other tasks (such as checkVibrations() or using uBit.display) have time to complete and display correctly.
*/
int timer = 10;

//Boolean to track whether the micro:bit is in "Connection Mode"
//While it is, vibration checking will pause to allow Bluetooth connections
bool connectionMode = false;

//Uses the accelerometer to check for vibrations
bool checkVibrations(){
	//For improved accuracy, the function samples the accelerometer 5 times at 100ms intervals
	int count = 0; //Keeps track of the number of times the vibrations are above the threshold
	for (int i=0; i<5; i++) {
		//If the absolute value of the difference of the x and y accelerometer values is over the threshold...
		if (sqrt(pow((x - uBit.accelerometer.getX()), 2) + pow((y - uBit.accelerometer.getY()), 2)) > threshold){
			count = count + 1; //...increment count by 1
		}
		else{
			count = count;
		}
		uBit.sleep(100);
	}
	if (count >= 3){ //If the majority of samples were positive...
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
    }
    else{
    	//WARNING: relies on correct x and y 'off' or 'nothing' values being assigned already!
    	uBit.serial.send("\r\ntoggle=true\r\n");

    	//Takes the differnce of the 'nothing' values and the 'on' values and  assigns the average as the threshold.
    	int xdiff = fabs(fabs(x) - fabs(xList[1]));
    	int ydiff = fabs(fabs(y) - fabs(yList[1]));
    	threshold = (xdiff + ydiff) / 2;
    	//threshold = sqrt(pow(fabs(fabs(x) - fabs(xList[1])), 2) + pow(fabs(fabs(y) - fabs(yList[1])), 2));
    }
}

//The main code loop
void beginChecking(){
	uBit.serial.send("\r\nBegin Checking");
	//MicroBitImage with all LEDs on, use to flash an alert when timer reaches 0
	MicroBitImage on("255,255,255,255, 255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n");
	while(connectionMode == false){ //Loop while the micro:bit is not in Connection Mode
		if (timer <= 0){ //When the timer has finished counting down...
			while (checkVibrations() == true){ //...flashes all LEDs until vibrations have stopped
				for (int i=0; i<5; i++) {
					uBit.display.clear();
					uBit.sleep(100);
					uBit.display.print(on);
					uBit.sleep(100);					
				}
			}
			timer = 10; //Resets timer
		}
		else if (checkVibrations() == true){ //If there are vibrations...
			uBit.sleep(1000);
			timer = timer - 1; //...decrement the timer by 1
		}
		else{ //Checks for false positives
			uBit.sleep(1000);
				if (checkVibrations() == false){ //If, after 1000ms, there are no vibrations, reset the timer
				timer = 10;
			}
			else{
				timer = timer - 1; //Otherwise, there really are vibrations, so decrement the timer by 1
			}
		}
		uBit.display.printAsync(timer); //Displays the current timer value to the micro:bit LEDs
		uBit.serial.send(timer);
	}
	//At this point the loop has ended and Connection Mode must have been initiated
	uBit.serial.send("\r\nLoop terminated.");
}

void onConnected(MicroBitEvent)
{
    uBit.display.print("C");
    uBit.sleep(5000); //Delays to allow the micro:bit time to start streaming data after the connection has been made
    connectionMode = false; //Device has now connected, so exit Connection Mode
    create_fiber(beginChecking); //(re)enters main code loop, where vibrations are continually checked
}

void onDisconnected(MicroBitEvent)
{
    uBit.display.print("D");
}

//Toggles Connection Mode if either Button A or Button B is pressed on the micro:bit
//In Connection Mode the micro:bit waits for a connection over BLE and starts the Accelerometer Service
void onButton(MicroBitEvent)
{
	//If the micro:bit is already in Connection Mode, exit it and return to checking vibrations
	if (connectionMode){
		connectionMode = false;
		uBit.serial.send("\r\nConnection Mode exited.");
    	create_fiber(beginChecking);
	}
	else{ //Otherwise, enter Connection Mode
		connectionMode = true;
		uBit.serial.send("\r\nConnection Mode initiated.");
		uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_CONNECTED, onConnected);
	    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_DISCONNECTED, onDisconnected);

	    new MicroBitAccelerometerService(*uBit.ble, uBit.accelerometer);
	    uBit.display.scroll("BLE...");
	}
}

int main() {
    uBit.init(); //Initialise the micro:bit runtime

    uBit.serial.send("\r\nSTART PROGRAM");
    //Valid periods: 1, 2, 5, 10, 20, 80, 160 and 640 (ms)
    uBit.accelerometer.setPeriod(160); //Sets the accelerometer sample rate to 160ms

    uBit.display.scroll("Calibrate...");

    //Waits until a button is pressed; the first time, the 'off' values are taken, then the 'on' values.
    int con = 0;
    while (con != 2){
    	bool pressed = false;
    	while (pressed == false){ //Waits until button A is pressed
	        if(uBit.buttonA.isPressed() or uBit.buttonB.isPressed()){
	        	if (con == 0){
	        		pressed = true;
					uBit.display.scroll("OFF");
		        	reset(false); //Initialise 'off' values
		        	con = con + 1;
	        	}
	        	else if (con == 1){
	        		pressed = true;
	        		uBit.display.scroll("ON");
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

    //Set up listeners for Button A and Button B; runs the same function regardless of which button is pressed
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButton);
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_B, MICROBIT_BUTTON_EVT_CLICK, onButton);

    create_fiber(beginChecking); //Enters main code loop, where vibrations are continually checked
    //Releases this fiber, meaning the program enters into the scheduler
    //Here, other fibers can finish running, after which the micro:bit simply enters a power efficient sleep
    release_fiber();
}