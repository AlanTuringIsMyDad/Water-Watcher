//TO-DO: adjust sensitivity, may need to reduce sampling rate or tweak threshold

#include <algorithm>
#include <cmath>
#include "MicroBit.h"
MicroBit uBit;

//'off' values for the accelerometer
int x;
int y;

int threshold;
Ticker timer;
int timerNum = 10;

void decrementTimer(){
	timerNum = timerNum - 1;
}

bool checkVibrations(){
	int currentx = uBit.accelerometer.getX();
	int currenty = uBit.accelerometer.getY();

	if (sqrt(pow((x - currentx), 2) + pow((y - currenty), 2)) < threshold){
		return false;
	}
	else{
		return true;
	}
}

void reset(bool toggle) { //Toggle is True if resetting 'on' values, False if resetting 'off' values
    int xList[3];
    int yList[3];

    for (int i=0; i<3; i++) {
        xList[i] = uBit.accelerometer.getX();
        yList[i] = uBit.accelerometer.getY();

        ManagedString xStr(xList[i]);
        ManagedString yStr(yList[i]);

        uBit.serial.send("\r\nX:");
        uBit.serial.send(xStr);
        uBit.serial.send("\r\nY:");
        uBit.serial.send(yStr);
        uBit.display.scroll("->");
    }
    std::sort(std::begin(xList), std::end(xList));
    std::sort(std::begin(yList), std::end(yList));

    if (toggle == false){
    	uBit.serial.send("\r\ntoggle=false\r\n");
    	x = xList[1];
    	y = yList[1];
    }
    else{
    	//WARNING: relies on correct x and y 'off' or 'nothing' values being assigned already!
    	uBit.serial.send("\r\ntoggle=true\r\n");
    	//int xdiff = fabs(fabs(x) - fabs(xList[1]));
    	//int ydiff = fabs(fabs(y) - fabs(yList[1]));
    	//TO-DO: should be this or sqrt(xdiff^2 + ydiff^2)??
    	//threshold = (xdiff + ydiff) / 2; //TO-DO: consolidate to 1 line for memory
    	threshold = sqrt(pow(fabs(fabs(x) - fabs(xList[1])), 2) + pow(fabs(fabs(y) - fabs(yList[1])), 2));
    }
}

void beginChecking(){
	uBit.serial.send("Begin Checking");
	timer.attach(&decrementTimer, 1.0);
	while(1){
		if (timerNum <= 0){
			while (checkVibrations() == true){
				MicroBitImage on("255,255,255,255, 255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n255,255,255,255,255\n");
				uBit.display.print(on);
				uBit.sleep(100);
				uBit.display.clear();
				uBit.sleep(100);
			}
			timerNum = 10;
		}
		else if (checkVibrations() == true){
			//uBit.display.print(timerNum);
		}
		else{
			uBit.sleep(500);
			if (checkVibrations() == false){
				timerNum = 10;
			}
		}
		uBit.display.print(timerNum);
		uBit.serial.send(timerNum);
	}
}

int main() {
    uBit.init();

    uBit.serial.send("\r\nSTART PROGRAM");
    //uBit.accelerometer.setPeriod(); //TO-DO: check approved periods

    int con = 0;
    while (con != 2){
    	bool pressed = false;
    	while (pressed == false){
	        if(uBit.buttonA.isPressed()){
	        	if (con == 0){
	        		pressed = true;
					uBit.display.scroll("OFF");
		        	reset(false); //Can create_fiber() be used with parameters?
		        	con = con + 1;
	        	}
	        	else if (con == 1){
	        		pressed = true;
	        		uBit.display.scroll("ON");
		        	reset(true); //Can create_fiber() be used with parameters?
		        	con = con + 1;
	        	}
	        	else{
	        		con = con;
	        	}
	        }
    	}
    }

    uBit.serial.send("\r\n");
    uBit.serial.send(x);
    uBit.serial.send("\r\n");
    uBit.serial.send(y);
    uBit.serial.send("\r\n");
    uBit.serial.send(threshold);

    // while (1){
    // 	uBit.serial.send("\r\nTimer:\r\n");
    // 	uBit.serial.send(timerNum);
    // 	if(uBit.buttonA.isPressed()){
    // 		bool result = checkVibrations();
    // 		uBit.serial.send(result);
    // 	}
    // }

    create_fiber(beginChecking);
    release_fiber(); // "release the fibers!!"
}