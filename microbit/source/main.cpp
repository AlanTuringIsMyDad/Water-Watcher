#include <algorithm>
#include "MicroBit.h"
MicroBit uBit;

void reset() {
    ManagedString msg("Checking accelerometer\n");
    uBit.serial.send(msg);

    bool pressed = false;
    while (pressed == false){
        if(uBit.buttonA.isPressed()){
            pressed = true;
            int xList[3];
            int yList[3];
            
            uBit.display.scroll("Button");
            for (int i=0; i<3; i++) {
                xList[i] = uBit.accelerometer.getX();
                yList[i] = uBit.accelerometer.getY();

                ManagedString x(xList[i]);
                ManagedString y(yList[i]);

                uBit.serial.send("\nX:");
                uBit.serial.send(x);
                uBit.serial.send("\nY:");
                uBit.serial.send(y);
                uBit.display.scroll("->");
            }
            std::sort(std::begin(xList), std::end(xList));
            int xMedian = xList[1];
            uBit.serial.send("\nX Median:");
            uBit.serial.send(xMedian);
            std::sort(std::begin(yList), std::end(yList));
            int yMedian = yList[1];
            uBit.serial.send("\nY Median:");
            uBit.serial.send(yMedian);
        }
    }
}

int main() {
    uBit.init();
    create_fiber(reset); // create fiber and schedule it.
    release_fiber(); // "release the fibers!!"
}