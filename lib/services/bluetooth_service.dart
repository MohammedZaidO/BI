import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

class BluetoothService {
  static const String deviceName = 'Classroom1';
  static const String serviceUuid = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
  static const String characteristicUuid = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
  
  bool _isConnected = false;
  Function(bool)? onConnectionChanged;
  
  BluetoothDevice? _device;
  StreamSubscription? _connectionSubscription;
  Timer? _monitoringTimer;
  BluetoothCharacteristic? _writeCharacteristic;
  
  BluetoothService();
  
  Future<bool> connect() async {
    try {
      if (await FlutterBluePlus.isSupported == false) return false;
      
      if (Platform.isAndroid) {
        try {
          await FlutterBluePlus.turnOn();
        } catch (e) {
          // Ignore if already on or permission denied
        }
      }
      
      await FlutterBluePlus.startScan(timeout: const Duration(seconds: 5));
      
      final Completer<bool> connectionCompleter = Completer();
      
      var subscription = FlutterBluePlus.scanResults.listen((results) async {
        for (ScanResult r in results) {
          if (r.device.platformName == deviceName || r.device.advName == deviceName) {
            _device = r.device;
            await FlutterBluePlus.stopScan();
            
            try {
              await _device!.connect(timeout: const Duration(seconds: 10), license: License.ivd);
              _isConnected = true;
              onConnectionChanged?.call(true);
              
              // Find characteristic for admin commands
              var services = await _device!.discoverServices();
              for (var service in services) {
                // Not using exact string match for custom service from flutter_blue_plus mapping, 
                // UUID formatting might slight differ, check canonical UUID
                if (service.uuid.toString().toLowerCase() == serviceUuid.toLowerCase()) {
                  for (var characteristic in service.characteristics) {
                    if (characteristic.uuid.toString().toLowerCase() == characteristicUuid.toLowerCase()) {
                      _writeCharacteristic = characteristic;
                    }
                  }
                }
              }
              
              _connectionSubscription?.cancel();
              _connectionSubscription = _device!.connectionState.listen((BluetoothConnectionState state) {
                if (state == BluetoothConnectionState.disconnected) {
                  _isConnected = false;
                  onConnectionChanged?.call(false);
                }
              });
              
              if (!connectionCompleter.isCompleted) {
                connectionCompleter.complete(true);
              }
            } catch (e) {
              if (!connectionCompleter.isCompleted) {
                connectionCompleter.complete(false);
              }
            }
            break;
          }
        }
      });
      
      Future.delayed(const Duration(seconds: 8), () {
        if (!connectionCompleter.isCompleted) {
          FlutterBluePlus.stopScan();
          connectionCompleter.complete(false);
        }
      });
      
      return await connectionCompleter.future;
    } catch (e) {
      print('Connection error: $e');
      return false;
    }
  }
  
  Future<void> disconnect() async {
    try {
      await _device?.disconnect();
      _isConnected = false;
      onConnectionChanged?.call(false);
    } catch (e) {
      print('Disconnect error: $e');
    }
  }
  
  Future<bool> checkConnection() async {
    return _isConnected;
  }
  
  Future<bool> sendAdminCommand(String command) async {
    if (!_isConnected || _writeCharacteristic == null) {
      bool reconnected = await connect();
      if (!reconnected || _writeCharacteristic == null) return false;
    }
    
    try {
      await _writeCharacteristic!.write(utf8.encode(command), withoutResponse: false);
      return true;
    } catch (e) {
      print('Write error: $e');
      return false;
    }
  }
  Future<List<String>> getConnectedClients() async {
    if (!_isConnected || _writeCharacteristic == null) {
      if (!await connect()) return [];
    }
    
    try {
      if (!_writeCharacteristic!.isNotifying) {
        await _writeCharacteristic!.setNotifyValue(true);
      }
      
      final Completer<List<String>> completer = Completer();
      
      final subscription = _writeCharacteristic!.onValueReceived.listen((value) {
        final str = utf8.decode(value);
        if (str == "NONE") {
          if (!completer.isCompleted) completer.complete([]);
        } else if (str.isNotEmpty && str.contains(":")) {
          // ensure it's a MAC address list
          if (!completer.isCompleted) completer.complete(str.split(',').where((s) => s.trim().isNotEmpty).toList());
        }
      });
      
      await sendAdminCommand('GET_CLIENTS');
      
      final result = await completer.future.timeout(
        const Duration(seconds: 5),
        onTimeout: () => [],
      );
      
      subscription.cancel();
      return result;
    } catch (e) {
      print('Get clients error: $e');
      return [];
    }
  }
  
  void startMonitoring() {
    // Monitoring is handled by the connection state stream.
    // Periodic polling is unnecessary and causes spammy snackbars.
  }
  
  Future<void> loadAndConnect() async {
    if (_isConnected) {
      onConnectionChanged?.call(true);
      return;
    }
    await connect();
  }
  
  bool get isConnected => _isConnected;
  bool get isScanning => FlutterBluePlus.isScanningNow;
  
  void dispose() {
    _monitoringTimer?.cancel();
    _connectionSubscription?.cancel();
    disconnect();
  }
}
