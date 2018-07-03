#include "MicroBit.h"
#include <string>
#include <cmath>
#include <sstream>

MicroBit uBit;
namespace globalVars{
	int currentx = pixel_from_g(uBit.accelerometer.getX());
	int currenty = pixel_from_g(uBit.accelerometer.getY());

	int x = pixel_from_g(uBit.accelerometer.getX());
	int y = pixel_from_g(uBit.accelerometer.getY());

	int con = 0;
	int TIMERVALUE = 0;
	int timer = TIMERVALUE;
	int threshold = 10;

	using namespace std;
	string log = "";
}


bool checkVibrations() {
	using namespace globalVars;
	int currentx = pixel_from_g(uBit.accelerometer.getX());
	int currenty = pixel_from_g(uBit.accelerometer.getY());

	int xdiff = cmath.fabs(cmath.fabs(x) - cmath.fabs(currentx));
	int ydiff = cmath.fabs(cmath.fabs(y) - cmath.fabs(currenty));

	if (cmath.sqrt(pow(xdiff, 2) + pow(ydiff, 2)) < threshold) {
		return False;
	}
	else {
		return True;
	}
	delay(500);
}

void reset(bool toggle) {
	using namespace globalVars;
	int xlist [3] = { 0, 0, 0 };
	int ylist [3] = { 0, 0, 0 };

	for( int i = 0; a < 3; a = a + 1 ) {
      delay(500);
      xlist [i] = pixel_from_g(uBit.accelerometer.getX());
      ylist [i] = pixel_from_g(uBit.accelerometer.getY());
    }

    sort(begin(xlist), end(xlist));
    sort(begin(ylist), end(ylist));

    if (toggle == false) {
    	x = xlist [1];
    	y = ylist [1];
    	std::string strX;
	    for (int i: xlist) {
	        str += std::to_string(i);
	    }
	    std::string strY;
	    for (int i: ylist) {
	        str += std::to_string(i);
	    }
    	log = log + "Off" + strX + strY;
    }
    else {
    	int xOn = xlist [1];
    	int yOn = ylist [1];

    	int xdiff = cmath.fabs(cmath.fabs(x) - cmath.fabs(currentx));
		int ydiff = cmath.fabs(cmath.fabs(y) - cmath.fabs(currenty));
		threshold = (xdiff + ydiff) / 2;
		for (int i: xlist) {
	        str += std::to_string(i);
	    }
	    std::string strY;
	    for (int i: ylist) {
	        str += std::to_string(i);
	    }
		log = log + "Off" + strX + strY + std::to_string(threshold);
    }
}

int main() {
	uBit.init();
	uBit.display.scroll("Hello World!");
	delay(500);

	while (con <= 2) {
		//Button stuff?
	}

	//Logs stuff

	while (1) {
		if (timer <= 0) {
			while (checkVibrations() == true) {
				//music
				delay(500);
			}
			timer = TIMERVALUE;
		}
		else if (checkVibrations == true) {
			timer = timer - 1;
			//Happy or timer value display
			delay(500);
		}
		else {
			delay(500);
			if (checkVibrations == false) {
				timer = TIMERVALUE;
			}
			else {
				//Blank print statement
			}
			//Show timer value display
			delay(500)
		}
	}

	release_fiber();
}