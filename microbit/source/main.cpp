#include "MicroBit.h"
#include "MicroBitUARTService.h"
MicroBit uBit;
MicroBitUARTService *uart;

int connected = 0;

void onConnected(MicroBitEvent e){
    uBit.display.scroll("CONNECTED")
    uBit.serial.send("CONNECTED\n");
    
    connected = 1;
}

void onDisconnected(MicroBitEvent e){
    uBit.display.scroll("DISCONNECTED");
    uBit.serial.send("DISCONNECTED\n");
    
    connected = 0;
}

void onButtonA(MicroBitEvent e){
    if (connected == 0){
        uBit.display.scroll("NOT CONNECTED");
        return;
    }
    uart->send("BUTTON A")
    uBit.display.scroll("BUTTON A")
    uBit.serial.send("BUTTON A\n");
}

int main(){
    uBit.init();

    uBit.messageBus.listen(MICROBIT_ID_BUTTON_A, MICROBIT_BUTTON_EVT_CLICK, onButtonA)
    
    release_fiber();
}