#include "MicroBit.h"
MicroBit uBit;

void onConnected(MicroBitEvent)
{
    uBit.display.print("C");
}

void onDisconnected(MicroBitEvent)
{
    uBit.display.print("D");
}

int main() {
    uBit.init();
    //create_fiber(reset); // create fiber and schedule it.

    uBit.display.scroll("Start");

    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_CONNECTED, onConnected);
    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_DISCONNECTED, onDisconnected);

    new MicroBitAccelerometerService(*uBit.ble, uBit.accelerometer);

    release_fiber(); // "release the fibers!!"
>>>>>>> Stashed changes
}