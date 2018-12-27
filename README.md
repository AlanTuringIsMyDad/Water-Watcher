# Water Watcher
An Android and BBC micro:bit Bluetooth Low Energy project, done for my Computer Science A Level project.

## Description
The project uses the BBC micro:bit, connected to an Android app via Bluetooth. The project is designed to save water; the micro:bit, contained in silicon, is strapped to a tap. Using the accelerometer, the micro:bit is able to sense vibrations to know when the tap is running; if it runs for too long, it will trigger an alert to remind the user to turn the tap off. The micro:bit connects to an Android app over Bluetooth. The app graphs the values measured from the micro:bit's accelerometer in real-time, and allows the user to change certain settings on the micro:bit, such as the length of the timer before the alert will trigger.

Although the system would be useful for anyone who regularly uses running water, it is primarily aimed at those who are more prone to forgetfulness. This would include families with young children, those with dyslexia, and the elderly, specifically those with dementia.

## Features
The project includes a number of features, some of which are fairly specialised. However, this repository may serve as a helpful example for Android BLE development, and in taking advantage of the micro:bit's Bluteooth functionality. Specifically, these include:
- Scanning for available Bluetooth devices
- Implementing a Bluetooth connection as an Android service
- Defining BLE behaviour in a Gatt Callback
- Specifically using the micro:bit's Bluetooth functionality in an Android app
- Advanced micro:bit functionality, such as using its persistent storage and asynchrony

## Dependencies
~~It is important to note that as of 06/08/2018, it is necessary to use v2.0.0-rc9 of the microbit-DAL for the BLE accelerometer service to work, as detailed here https://github.com/lancaster-university/microbit-dal/issues/366~~
The program is designed and tested using microbit-DAL v2.1.0, although the program may work with older versions as well.

The Android app makes use of the GraphView library by Jonas Gehring www.android-graphview.org and the ExpandableCardView library by Alessandro Sperotti. https://github.com/AleSpero/ExpandableCardView

## Usage
### Using the micro:bit program
To interact with the micro:bit, use either or both of its two buttons, Button A (left) and Button B (right). ![Button A on the left of the micro:bit, Button B on the right](https://microbit.org/images/microbit-features-buttons.png)

When the micro:bit is first booted, it will need to be calibrated. Assuming the micro:bit is strapped onto the tap, first press either button while the tap is off, and then again when the tap is running. This will calibrate the micro:bit to the tap in question, and this calibration will be saved in future. At this point, the micro:bit will begin checking the tap - using vibration levels measured from the accelerometer, and compared against the results from caibration - continually. While the tap is running, a timer will count down and an alert will trigger when that timer reaches zero. To turn off the alert or reset the timer at any point, simply switch off the tap. 
![The micro:bit's accelerometer measuring movement in the X, Y and Z axis](https://microbit.org/images/microbit-features-accelerometer.png)

The micro:bit's program has five different "modes", used for different features. To navigate between these modes, the micro:bit's buttons are used.

1. Calibration Mode: this is initiated the first time the micro:bit is booted. The micro:bit can be recalibrated by pressing Button A.

2. Vibration Checking Mode: the main part of the program, where the tap is continually checked, and the timer and alert is handled.

3. Connection Mode: this is where the micro:bit can connect to the smartphone app. Press Button B to enter this mode, at which point the micro:bit will wait for a connection to be initiated. Simply press Button B again to manually exit it if no connection is made.

4. Settings Mode: once the micro:bit is connected to the smartphone, pressing Button B again will enter Settings Mode. Here, the app can update various micro:bit settings over Bluetooth, such as the length of the timer.

6. Reset Mode: pressing both Button A and Button B simultaneously will enter the micro:bit into Reset Mode. Press both buttons again to reset the program completely, removing all stored values and reverting it to its state as if it had been booted for the first time. Press either button individually to cancel and exit this mode.

### Connecting to the micro:bit using the app
To connect to the micro:bit, first make sure that it is paired with your smartphone. Detailed instructions on this process can be found here: https://makecode.microbit.org/reference/bluetooth/bluetooth-pairing It is worth noting that in the current [config.json](microbit/config.json),  the passkey is disabled.

Once the micro:bit is paired, make sure that it is in Connection Mode by pressing Button B (the rightmost button), then simply open the app, scan for available devices, and then select your micro:bit, which will be highlighted in green. This will connect to the micro:bit, which will re-enter Vibration Checking mode, and enable the other Bluetooth features of the app.

To reselect a device at any time, simply pick "Select Device" from the navigation bar within the app, which will allow you to re-scan for available devices and select the one you'd like to connect to. Please note that re-entering this screen will cause the current device to be disconnected.

### Graphing
Once the micro:bit is connected, it will transmit the values measured from its accelerometer to the app, which is automatically graphed in real-time, and can be exported.

### Settings
The app can update various micro:bit settings over Bluetooth. To do this, first make sure the micro:bit is connected and in Settings Mode. To send the current settings to the app to be displayed, press Button A while in this mode. To update the settings on the micro:bit, enter the new values in the app and press the button to send these settings over BLE. To exit this mode on the micro:bit, either update the settings and the mode will be exited automatically, or press the button in the app to cancel updating settings. You can also revert to the default values in the app by pressing the button to reset values to their defaults. This will not revert the settings on the micro:bit until those values are sent over Bluetooth.

All input is validated to make sure that the values are appropriate for all settings. The settings and their validation is as follows:

|Setting|Explanation|Validation|
|--|--|--|
|Timer Length (Seconds)|The length of the timer used in Vibration Checking mode on the micro:bit in seconds.|An integer between 0 and 1800.|
|Accelerometer Period (milliseconds)|The period – time in milliseconds between measurements – of the micro:bit’s accelerometer. |One of the following values: 2, 5, 10, 20, 80, 160, 640.|
|Number of Accelerometer Samples|The number of accelerometer samples the micro:bit takes while checking vibrations.|An integer between 1 and 50.|
|X|The value of the accelerometer's X-axis in calibration, corresponding to the tap being off.|An integer between -1024 and 1024.|
|Y|The value of the accelerometer's Y-axis in calibration, corresponding to the tap being off.|An integer between -1024 and 1024.|
|Threshold|The threshold of vibrations above which the micro:bit considers the tap to be running.|An integer between 0 and 1448.|

The micro:bit measures values between -1024 and 1024 milli-g on each axis. The threshold is the absolute value of both axes, and √1024² + 1024² ≈ 1448. The micro:bit's accelerometer will only accept certain periods; although it will also accept a period of 1ms, due to the polling frequency that would cause it will result in the Water Watcher program crashing due to memory issues. All other validation is done to ensure that all values are sensible in context.

## Built with and Acknowledgments
The project uses the BBC micro:bit. Its website can be found here https://microbit.org/, and documentation for the the micro:bit runtime, used in this project, can be found here https://lancaster-university.github.io/microbit-docs/ Many thanks for this fantastic and detailed documentation, which has been an invaluable resource in learning how to use the features of the micro:bit. Thanks specifically to [Martin Woolley](https://twitter.com/bluetooth_mdw) who was very helpful in resolving an [initial problem with the micro:bit's BLE accelerometer service](https://github.com/lancaster-university/microbit-dal/issues/366) and who's [micro:bit Blue](https://github.com/microbit-foundation/microbit-blue) application served as a constant guide on how to connect an Android app with a micro:bit over BLE.

Android development was done using [Android Studio](https://developer.android.com/studio/), with some icons from https://material.io/tools/icons/. Thanks to Shahar Avigezer for their very helpful [blog post](https://medium.com/@avigezerit/bluetooth-low-energy-on-android-22bc7310387a) on Android BLE, which sped up the learning process significantly. 