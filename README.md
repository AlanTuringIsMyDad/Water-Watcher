# Water-Watcher
My Computer Science A Level project.

The project uses the BBC micro:bit, connected to an Android app via Bluetooth. The project is designed to save water; the micro:bit, contained in silicon, is strapped to a tap. Using the accelerometer, the micro:bit is able to sense vibrations to know when the tap is running; if it runs for too long, it will trigger an alert to remind the user to turn the tap off. This will connect to an Android app that allows the user to change certain settings, such as the length of the timer before the alert will trigger. As well as that, I would like it to display certain graphs and perform statistical functions; eg. being able to see the vibration levels in real-time as they come in, and perhaps to record a session, and then analyse the recorded data afterwards.

Although the system would be useful for anyone who regularly uses running water, it is primarily aimed at those who are more prone to forgetfulness. This would include families with young children, those with dyslexia, and the elderly, specifically those with dementia.

# Requirements
It is important to note that as of 06/08/2018, it is necessary to use v2.0.0-rc9 of the microbit-DAL for the BLE accelerometer service to work, as detailed here https://github.com/lancaster-university/microbit-dal/issues/366

The Android app makes use of the GraphView library by Jonas Gehring. www.android-graphview.org

# Usage
Before using the Bluetooth functionality in the app, please make sure the micro:bit is paired with the phone! Instructions on how to do so can be found here, although with the current version of the program, entering the PIN is not necessary. https://makecode.microbit.org/reference/bluetooth/bluetooth-pairing 

When using the micro:bit program, please firstly calibrate the micro:bit by pressing either button while there are no vibrations (eg. tap is turned off), then pressing again when there are vibrations. (eg. tap is turned on) To connect the app to the micro:bit over BLE, press either button on the micro:bit while the main section of code (checking for vibrations) is running to enter Connection Mode, where the micro:bit will wait for a Bluetooth connection before continuing. If you want to manually exit Connection Mode, simply press either button on the micro:bit again.