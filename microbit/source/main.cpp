#include "MicroBit.h"
#include "MicroBitUARTService.h"
MicroBit uBit;
MicroBitUARTService *uart;

bool connected = false;

void onConnected(MicroBitEvent e){
    uBit.display.scroll("CONNECTED");
    uBit.serial.send("CONNECTED\n");
    
    connected = true;
    
    ManagedString eom(":");

    while (connected == 1) {
        ManagedString msg = uart->readUntil(eom);
        uBit.display.scroll(msg);
    }
}

void onDisconnected(MicroBitEvent e){
    uBit.display.scroll("DISCONNECTED");
    uBit.serial.send("DISCONNECTED\n");
    
    connected = false;
}

void onButtonA(MicroBitEvent e){
    if (connected == false){
        uBit.display.scroll("NOT CONNECTED");
        return;
    }
    uart->send("BUTTON A");
    uBit.display.scroll("BUTTON A");
    uBit.serial.send("BUTTON A\n");
}

int main(){
    uBit.init();
    uBit.serial.send("START\n");
    
    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_CONNECTED, onConnected);
    uBit.messageBus.listen(MICROBIT_ID_BLE, MICROBIT_BLE_EVT_DISCONNECTED, onDisconnected);
    
    uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA);
    
    uart = new MicroBitUARTService(*uBit.ble, 32, 32); 
    uBit.display.scroll("AVM");
    
    release_fiber();
}