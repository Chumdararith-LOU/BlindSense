#include <WiFi.h>
#include <WiFiUdp.h>
const char* SSID = "YOUR_HOTSPOT_SSID";   // phone hotspot name
const char* PASS = "YOUR_HOTSPOT_PASS";
WiFiUDP udp;
const int PORT = 8888;
const int MOTOR_L = 26;   // via MOSFET/driver transistor
const int MOTOR_R = 27;
uint8_t buf[16];

void setup() {
  Serial.begin(115200);
  WiFi.begin(SSID, PASS);
  while (WiFi.status() != WL_CONNECTED) { delay(300); Serial.print("."); }
  Serial.printf("\nBelt IP: %s\n", WiFi.localIP().toString().c_str());
  udp.begin(PORT);
  ledcSetup(0, 1000, 8); ledcAttachPin(MOTOR_L, 0);
  ledcSetup(1, 1000, 8); ledcAttachPin(MOTOR_R, 1);
}

void loop() {
  if (udp.parsePacket() >= 4) {
    int len = udp.read(buf, sizeof(buf));
    if (len >= 4 && buf[0] == 0xA5 && buf[3] == (uint8_t)(0xA5 ^ buf[1] ^ buf[2])) {
      ledcWrite(0, buf[1]);
      ledcWrite(1, buf[2]);
      Serial.printf("L=%d R=%d\n", buf[1], buf[2]);
    }
  }
  delay(5);
}
