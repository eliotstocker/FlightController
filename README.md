# FlightController
![Icon](icon.png)

Android base Quadrocopter flight platform using WiFi direct to connect between Android Quadrocopter brain and control device

**This Project is a work in progress and as yet has not been fully tested for flight**

if you use this get used to the idea of loosing or crashing your quadrocopter until some further stability etc are added
hardware secs are to come including circuit diagrams for an Arduino Shield for motor control etc.

TODO:
-----
1. Add Gyroscopic stabilisation
2. Add Streaming from Device camera to controller (Started some work with LibStreaming)
3. Add Servo controls for attaching gimble etc
4. Tidy up Wifi Direct Connections (Stability is an issue currently)
5. Add Bluetooth Device Connection for Control Device (i.e. game controller)
6. GPS Follow mode
7. Flight Planning (Setting way points etc)
8. Return to me mode for lost connections. (using last known GPS Location of controller)
9. Internet control mode using web sockets for communication over long distances
10. Look at further Multicopter designs (6 motors etc)
11. Look at streaming from GoPro Etc back to Control Device for FPV
12. loads more