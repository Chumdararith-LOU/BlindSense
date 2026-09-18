#include <WiFi.h>
#include <WiFiUdp.h>

const char* ssid = "YOUR_WIFI_SSID";
const char* password = "YOUR_WIFI_PASSWORD";

WiFiUDP udp;
const unsigned int localUdpPort = 8888;
char packetBuffer[255];

// Motor PWM Pins
const int MOTOR_LEFT_PIN = 12;
const int MOTOR_RIGHT_PIN = 13;

// PWM Properties
const int PWM_FREQ = 5000;
const int PWM_RES = 8; // 0-255 range
const int CHANNEL_LEFT = 0;
const int CHANNEL_RIGHT = 1;

void setup() {
  Serial.begin(115200);

  // Setup PWM channels
  ledcSetup(CHANNEL_LEFT, PWM_FREQ, PWM_RES);
  ledcSetup(CHANNEL_RIGHT, PWM_FREQ, PWM_RES);
  ledcAttachPin(MOTOR_LEFT_PIN, CHANNEL_LEFT);
  ledcAttachPin(MOTOR_RIGHT_PIN, CHANNEL_RIGHT);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  udp.begin(localUdpPort);
  Serial.printf("\nESP32 Belt Ready. IP: %s, Port: %d\n", WiFi.localIP().toString().c_str(), localUdpPort);
}

void loop() {
  int packetSize = udp.parsePacket();
  if (packetSize) {
    int len = udp.read(packetBuffer, 255);
    if (len > 0) packetBuffer[len] = 0;

    int leftPwm = 0, rightPwm = 0;
    if (sscanf(packetBuffer, "L:%d,R:%d", &leftPwm, &rightPwm) == 2) {
      ledcWrite(CHANNEL_LEFT, constrain(leftPwm, 0, 255));
      ledcWrite(CHANNEL_RIGHT, constrain(rightPwm, 0, 255));
    }
  }
}
