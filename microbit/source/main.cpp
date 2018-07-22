#include "MicroBit.h"
MicroBit uBit;

void checkVibrations() {
    ManagedString msg("Checking accelerometer\n");
    uBit.serial.send(msg);

    while (1){ 
        uBit.accelerometer.setPeriod(1000);
        ManagedString x(uBit.accelerometer.getX());
        ManagedString y(uBit.accelerometer.getY());

        uBit.serial.send("\nX:");
        uBit.serial.send(x);
        uBit.serial.send("\nY:");
        uBit.serial.send(y);
        uBit.display.scroll("->");
    }
}

int main() {
    uBit.init();
    create_fiber(checkVibrations); // create fiber and schedule it.
    release_fiber(); // "release the fibers!!"
}