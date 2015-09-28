#include <RGBConverter.h>
#include <Wire.h>
#include <Servo.h>
#include <EEPROM.h>

#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

#define  LED1_RED     4
#define  LED1_GREEN   3
#define  LED1_BLUE    2

#define  NUM_MOTORS   4

AndroidAccessory acc("Pirate Media",
"QC1.0",
"Quadrocopter V1.0 (Beta)",
"1.0",
"http://www.piratemedia.tv/qc/",
"0000000012345678");

Servo escs[NUM_MOTORS];
int ESCStart = 8;
boolean connected = false;
boolean LEDOn = false;
int LEDHue = 0;
int LEDColor[3];
int CurrentSpeed[4];
int LEDBrightness = 127;
boolean LEDIn = false;
int Mspeed = 40;

void setup();
void loop();

void init_leds()
{
  digitalWrite(LED1_RED, 1);
  digitalWrite(LED1_GREEN, 1);
  digitalWrite(LED1_BLUE, 1);

  pinMode(LED1_RED, OUTPUT);
  pinMode(LED1_GREEN, OUTPUT);
  pinMode(LED1_BLUE, OUTPUT);
  
  delay(20);
  digitalWrite(LED1_RED, 0);
  digitalWrite(LED1_GREEN, 0);
  digitalWrite(LED1_BLUE, 0);
}

void init_values() {
  if(EEPROM.read(0) == 0x0) {
    LEDOn = false;
  } else {
    LEDOn = true;
  }
  LEDHue = map(EEPROM.read(1), 0, 255, 0, 360);
}

void setup()
{
  Serial.begin(115200);
  Serial.print("\r\nStart");

  init_leds();
  init_values();

  Serial.print("\r\nAttach Speed Controllers");
  for(int i = 0; i < NUM_MOTORS; i++) {
    Serial.print("\r\nAttach ESC "+String(i));
    escs[i].attach(ESCStart + i);
    escs[i].write(0);
    CurrentSpeed[i] = 0;
  }
  delay(2000);
  for(int i = 0; i < NUM_MOTORS; i++) {
    escs[i].write(20);
  }
  delay(1000);
  Serial.print("\r\nSpeed Controllers initialized");
  for(int i = 0; i < NUM_MOTORS; i++) {
    escs[i].write(0);
  }
  acc.powerOn();
}

void loop()
{
  byte err;
  byte idle;
  static byte count = 0;
  byte msg[3];
  long touchcount;
  
  /*
  //Test Motors
  escs[0].write(Mspeed);
  escs[1].write(Mspeed);
  escs[2].write(Mspeed);
  escs[3].write(Mspeed);
  delay(100);
  Mspeed++;
  
  if(Mspeed > 180) {
    Mspeed = 40;
  }
  
  return;*/

  if (acc.isConnected()) {
    if(!connected) {
      Serial.print("\r\nConnected to device.");
      connected = true;
    }
    int len = acc.read(msg, sizeof(msg), 1);
    int i;
    byte b;
    uint16_t val;
    int x, y;
    char c0;

    if (len > 0) {
      if (msg[0] == 0x4 && len > 4) {
        Serial.print("\r\nSet Speeds: "+String(msg[1])+", "+String(msg[2])+", "+String(msg[3])+", "+String(msg[4]));
        for(int i = 0; i < NUM_MOTORS; i++) {
          CurrentSpeed[i] = msg[i + 1];
        }
      } else if(msg[0] == 0x0) {
        //send app init values
        Serial.print("\r\ninit app.");
        msg[0] = 0x1;
        msg[1] = LEDOn ? 0x1 : 0x0;
        acc.write(msg, 2);
        
        msg[0] = 0x2;
        msg[1] = map(LEDHue, 0, 360, 0, 255);
        acc.write(msg, 2);
      } else if(msg[0] == 0x5) {
        if (msg[1] == 0x0) {
          //Turn LED On
          if(!LEDOn) {
            LEDOn = true;
            EEPROM.write(0, 0x1);
          }
        } else if (msg[1] == 0x1) {
          //Turn LED Off
          int LEDBrightness = 127;
          if(LEDOn) {
            LEDOn = false;
            EEPROM.write(0, 0x0);
            LEDSOff();
          }
        } else if (msg[1] == 0x2) {
          //set Color Hue for LED 1
          LEDHue = map(msg[2], 0, 255, 0, 360);
          EEPROM.write(1, msg[2]);
        }
      } else {
        Serial.print("\r\nUnknown Command Recieved.");
      }
    }
  } else {
    if(connected) {
      Serial.print("\r\nDisconnected from device.");
      connected = false;
    }
    
    //turn off all motors as device is disconnected
    //(should we do this? could the device disconnect in the air?)
    for(int i = 0; i < NUM_MOTORS; i++) {
          CurrentSpeed[i] = 0;
        }
  }
  
  for(int i = 0; i < NUM_MOTORS; i++) {
    escs[i].write(CurrentSpeed[i]);
  }
  
  if(LEDOn) {
    //next step for Brightness fade;
    if(LEDBrightness >= 255) {
      LEDIn = false;
      LEDBrightness = 255;
    } else if(LEDBrightness <= 0) {
      LEDIn = true;
      LEDBrightness = 0;
    }
    //add code to blink light
    setLEDColor(LEDBrightness);
    if(LEDIn) {
      LEDBrightness += 2;
    } else {
      LEDBrightness -= 2;
    }
  }
  
  //delay(10);
}

void setLEDColor(int brightness)
{
  byte rgb[3];
  RGBConverter conv;
  conv.hsvToRgb(double(LEDHue) / 360.0, 1.0, double(brightness) / 255.0, rgb);
  
  LEDColor[0] = int(rgb[0]);
  LEDColor[1] = int(rgb[1]);
  LEDColor[2] = int(rgb[2]);
    
  analogWrite(LED1_RED, LEDColor[0]);
  analogWrite(LED1_GREEN, LEDColor[1]);
  analogWrite(LED1_BLUE, LEDColor[2]);
}

void LEDSOff() {
  digitalWrite(LED1_RED, 0);
  digitalWrite(LED1_GREEN, 0);
  digitalWrite(LED1_BLUE, 0);
}

