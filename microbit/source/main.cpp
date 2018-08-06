#include "MicroBit.h"
<<<<<<< Updated upstream
#include "MicroBitUARTService.h"

MicroBit uBit;
MicroBitUARTService *uart;

int connected = 0;

void onConnected(MicroBitEvent)
{
    uBit.display.scroll("C");
    connected = 1;

    // "micro:bit Blue" mobile app used for initial testing will send ASCII strings terminated with the colon character
    ManagedString eom(":");

    while(connected == 1) {
        ManagedString msg = uart->readUntil(eom);
        uBit.display.scroll(msg);
    }

}

void onDisconnected(MicroBitEvent)
{
    uBit.display.scroll("D");
    connected = 0;
}

void onButtonA(MicroBitEvent)
{
    if (connected == 0) {
        return;
    }
    uart->send(ManagedString("YES"));
    uBit.display.scroll("Y");
}

void onButtonB(MicroBitEvent)
{
    if (connected == 0) {
        return;
    }
    uart->send(ManagedString("NO"));
    uBit.display.scroll("N");
}

void onButtonAB(MicroBitEvent)
{
    if (connected == 0) {
        return;
    }
    uart->send(ManagedString("GOT IT!!"));
    uBit.display.scroll("!");
}

int main()
{
    // Initialise the micro:bit runtime.
    uBit.init();

    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_CONNECTED, onConnected);
    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_DISCONNECTED, onDisconnected);
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA);
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_B, MICROBIT_BUTTON_EVT_CLICK, onButtonB);
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_AB, MICROBIT_BUTTON_EVT_CLICK, onButtonAB);

    /*In config.json, to free up enough memory for BLE services:
        "nested_heap_proportion": 0.50,
        "gatt_table_size": "0x700"
    */
    uart = new MicroBitUARTService(*uBit.ble, 32, 32);
    uBit.display.scroll("UART");

    new MicroBitButtonService(*uBit.ble);
    uBit.display.scroll("Button");

    new MicroBitLEDService(*uBit.ble, uBit.display);
    uBit.display.scroll("LED");

    new MicroBitTemperatureService(*uBit.ble, uBit.thermometer);
    uBit.display.scroll("Temperature");

    //new MicroBitMagnetometerService(*uBit.ble, uBit.compass);
    //uBit.display.scroll("Compass");

    // new MicroBitAccelerometerService(*uBit.ble, uBit.accelerometer);
    // uBit.display.scroll("Accelerometer");

    // If main exits, there may still be other fibers running or registered event handlers etc.
    // Simply release this fiber, which will mean we enter the scheduler. Worse case, we then
    // sit in the idle task forever, in a power efficient sleep.
    release_fiber();
=======
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