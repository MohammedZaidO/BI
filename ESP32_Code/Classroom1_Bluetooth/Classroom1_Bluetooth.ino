#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <vector>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

#define VIBRATOR_PIN        13 // Typical GPIO for vibrator/buzzer

BLEServer* pServer = NULL;
int clientsConnected = 0;
bool isVibrating = false;
unsigned long vibrationEndTime = 0;

struct ClientInfo {
  uint16_t conn_id;
  String mac;
};
std::vector<ClientInfo> connectedClients;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t *param) {
      clientsConnected++;
      char macStr[18];
      snprintf(macStr, sizeof(macStr), "%02X:%02X:%02X:%02X:%02X:%02X",
              param->connect.remote_bda[0], param->connect.remote_bda[1],
              param->connect.remote_bda[2], param->connect.remote_bda[3],
              param->connect.remote_bda[4], param->connect.remote_bda[5]);
      
      ClientInfo info;
      info.conn_id = param->connect.conn_id;
      info.mac = String(macStr);
      connectedClients.push_back(info);
      
      Serial.printf("Client connected! MAC: %s Total: %d\n", macStr, clientsConnected);
      BLEDevice::startAdvertising();
    }

    void onDisconnect(BLEServer* pServer, esp_ble_gatts_cb_param_t *param) {
      clientsConnected--;
      for (int i = 0; i < connectedClients.size(); i++) {
        if (connectedClients[i].conn_id == param->disconnect.conn_id) {
          Serial.printf("Client disconnected! MAC: %s Total: %d\n", connectedClients[i].mac.c_str(), clientsConnected);
          connectedClients.erase(connectedClients.begin() + i);
          break;
        }
      }
      BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      std::string rxValue = pCharacteristic->getValue();
      if (rxValue.length() > 0) {
        String msg = String(rxValue.c_str());
        Serial.print("Received: ");
        Serial.println(msg);
        
        if (msg.indexOf("VIBRATE") >= 0) {
           Serial.println("EMERGENCY ALERT: Triggering Hardware Vibration!");
           isVibrating = true;
           vibrationEndTime = millis() + 3000; // Vibrate for 3 seconds
           digitalWrite(VIBRATOR_PIN, HIGH);
        }
        else if (msg.indexOf("DISCONNECT_ALL") >= 0) {
          Serial.println("Admin requested DISCONNECT_ALL. Dropping all client connections...");
          for (int i = 0; i < connectedClients.size(); i++) {
            pServer->disconnect(connectedClients[i].conn_id);
          }
        }
        else if (msg.indexOf("GET_CLIENTS") >= 0) {
          String clientList = "";
          for (int i = 0; i < connectedClients.size(); i++) {
            clientList += connectedClients[i].mac;
            if (i < connectedClients.size() - 1) {
              clientList += ",";
            }
          }
          if (clientList == "") {
             clientList = "NONE";
          }
          pCharacteristic->setValue(clientList.c_str());
          pCharacteristic->notify();
          Serial.println("Sent client list: " + clientList);
        }
        else if (msg.indexOf("DISCONNECT_") >= 0) {
          String targetMac = msg.substring(11); // After "DISCONNECT_"
          targetMac.trim();
          for (int i = 0; i < connectedClients.size(); i++) {
            if (connectedClients[i].mac == targetMac) {
              Serial.println("Admin requested manual disconnect for MAC: " + targetMac);
              pServer->disconnect(connectedClients[i].conn_id);
              break;
            }
          }
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  pinMode(VIBRATOR_PIN, OUTPUT);
  digitalWrite(VIBRATOR_PIN, LOW);
  delay(1000);
  Serial.println("=================================");
  Serial.println("ESP32 BLE Classroom Server");
  Serial.println("=================================");
  
  BLEDevice::init("Classroom1");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());
  
  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *pCharacteristic = pService->createCharacteristic(
                                         CHARACTERISTIC_UUID,
                                         BLECharacteristic::PROPERTY_READ |
                                         BLECharacteristic::PROPERTY_WRITE |
                                         BLECharacteristic::PROPERTY_NOTIFY
                                       );
                                       
  pCharacteristic->setCallbacks(new MyCallbacks());
  pCharacteristic->setValue("Classroom Ready");
  
  pService->start();
  
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  
  pAdvertising->setMinPreferred(0x12);
  
  BLEDevice::startAdvertising();
  Serial.println("BLE Server is advertising and ready for multiple connections!");
}

void loop() {
  if (isVibrating && millis() > vibrationEndTime) {
    isVibrating = false;
    digitalWrite(VIBRATOR_PIN, LOW);
    Serial.println("Hardware Alert Finished.");
  }
  delay(10);
}
