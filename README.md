# FlightController
![Icon](icon.png)

Android base Quadrocopter flight platform using WiFi direct to connect between Android Quadrocopter brain and control device

Whats working:
--------------
* On Screen Controls
* Bluetooth Controller
* Bluetooth Connection between controller and computer
* Flight Control (without stabilization)
* Streaming back of Long, Lat, Altitude Stablization offset values
* LED Control for Onboard LEDs

**This Project is a work in progress and as yet has not been fully tested for flight**

if you use this get used to the idea of loosing or crashing your quadrocopter until some further stability etc are added
hardware secs are to come including circuit diagrams for an Arduino Shield for motor control etc.

TODO:
-----
1. Add Stabilisation (started using accelerometer, nit quite there yet)
2. Add Streaming from Device camera to controller (Started some work with LibStreaming)
3. Add Servo controls for attaching gimble etc
4. GPS Follow mode
5. Flight Planning (Setting way points etc)
6. Return to me mode for lost connections. (using last known GPS Location of controller)
7. Internet control mode using web sockets for communication over long distances
8. Look at further Multicopter designs (6 motors etc)
9. Look at streaming from GoPro Etc back to Control Device for FPV
10. Look into other comunication methods (Wifi Direct, RF etc) 
11. loads more
