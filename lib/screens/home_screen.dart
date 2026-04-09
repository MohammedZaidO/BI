import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/bluetooth_service.dart';
import '../services/phone_service.dart';
import '../services/contacts_service.dart';
import 'emergency_contacts_screen.dart';
import 'admin_dashboard_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final BluetoothService _bluetoothService = BluetoothService();
  bool _isConnected = false;
  bool _isScanning = false;
  bool _isSilentMode = false;
  List<EmergencyContact> _emergencyContacts = [];
  
  @override
  void initState() {
    super.initState();
    _initializeBluetooth();
    _checkSilentMode();
    _loadEsp32MacAddress();
    _loadContacts();
    ContactsService.syncAllToSystem();
  }
  
  void _loadContacts() async {
    final contacts = await ContactsService.getEmergencyContacts();
    if (mounted) {
      setState(() {
        _emergencyContacts = contacts;
      });
    }
  }
  
  void _loadEsp32MacAddress() {
    // Load saved MAC address if available
    // User can set this after getting it from ESP32 Serial Monitor
    // For now, we'll try without it first
  }
  
  void _initializeBluetooth() {
    _bluetoothService.onConnectionChanged = (connected) {
      if (!mounted) return;
      setState(() {
        _isConnected = connected;
        _isScanning = false;
      });
      
      if (connected) {
        _handleClassroomModeEnable();
      } else {
        _handleClassroomModeDisable();
      }
    };
    
    _bluetoothService.loadAndConnect();
  }
  
  Future<void> _handleClassroomModeEnable() async {
    _showNotification('Connected', 'Initializing Classroom Mode...');
    
    // 1. Request Permissions for detection layer
    final permissionsGranted = await PhoneService.ensurePermissions();
    if (!permissionsGranted) {
       _showNotification('Action Required', 'Please grant DND and Call Log access.');
    }

    // 2. Enable Logic
    final success = await PhoneService.enableClassroomMode();
    if (success) {
      _showNotification('Active', 'Classroom Mode enabled (Safe-Screening)');
    }
    _checkSilentMode();
  }

  Future<void> _handleClassroomModeDisable() async {
    await PhoneService.disableClassroomMode();
    _checkSilentMode();
    _showNotification('Disconnected', 'Classroom Mode disabled');
  }
  
  Future<void> _checkSilentMode() async {
    final isSilent = await PhoneService.isClassroomModeEnabled();
    if (mounted) {
      setState(() {
        _isSilentMode = isSilent;
      });
    }
  }
  
  void _showNotification(String title, String message) {
    HapticFeedback.lightImpact();
    if (!mounted) return;
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$title: $message'),
        duration: const Duration(seconds: 3),
        backgroundColor: _isConnected ? Colors.green : Colors.orange,
      ),
    );
  }
  
  Future<void> _toggleConnection() async {
    if (_isConnected) {
      await _bluetoothService.disconnect();
      setState(() {
        _isConnected = false;
      });
    } else {
      setState(() {
        _isScanning = true;
      });
      final connected = await _bluetoothService.connect();
      setState(() {
        _isConnected = connected;
        _isScanning = false;
      });
      if (!connected) {
        _showNotification('Connection Failed', 'Make sure ESP32 is paired and powered on');
      }
    }
  }
  
  @override
  void dispose() {
    _bluetoothService.onConnectionChanged = null;
    PhoneService.disableClassroomMode(); // Safety reset
    _bluetoothService.dispose();
    super.dispose();
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Smart Silent Device'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        actions: [
          IconButton(
            icon: const Icon(Icons.admin_panel_settings),
            onPressed: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const AdminDashboardScreen(),
                ),
              );
            },
            tooltip: 'Admin Dashboard',
          ),
          IconButton(
            icon: const Icon(Icons.contacts),
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const EmergencyContactsScreen(),
                ),
              );
              _loadContacts();
            },
            tooltip: 'Favourite Contacts',
          ),
        ],
      ),
      drawer: Drawer(
        child: ListView(
          padding: EdgeInsets.zero,
          children: const [
            DrawerHeader(
              decoration: BoxDecoration(color: Colors.blue),
              child: Text(
                'Smart Silent Device',
                style: TextStyle(color: Colors.white, fontSize: 24),
              ),
            ),
            ListTile(
              title: Text('Developers', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            ),
            ListTile(
              leading: Icon(Icons.person),
              title: Text('Mohammed Noman Shaik'),
            ),
            ListTile(
              leading: Icon(Icons.person),
              title: Text('Mohd Abrar'),
            ),
            ListTile(
              leading: Icon(Icons.person),
              title: Text('Umair Abdul Ghani'),
            ),
          ],
        ),
      ),
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Theme.of(context).colorScheme.primaryContainer,
              Theme.of(context).colorScheme.surface,
            ],
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Spacer(),
                
                // Device Icon
                Container(
                  width: 150,
                  height: 150,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _isConnected 
                        ? Colors.green.withOpacity(0.2)
                        : Colors.grey.withOpacity(0.2),
                    border: Border.all(
                      color: _isConnected ? Colors.green : Colors.grey,
                      width: 3,
                    ),
                  ),
                  child: Icon(
                    _isConnected ? Icons.bluetooth_connected : Icons.bluetooth_disabled,
                    size: 80,
                    color: _isConnected ? Colors.green : Colors.grey,
                  ),
                ),
                
                const SizedBox(height: 40),
                
                // Status Text
                Text(
                  _isConnected ? 'Connected to Classroom1' : 'Not Connected',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                    color: _isConnected ? Colors.green : Colors.grey[700],
                  ),
                ),
                
                const SizedBox(height: 16),
                
                // Silent Mode Status
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  decoration: BoxDecoration(
                    color: _isSilentMode 
                        ? Colors.orange.withOpacity(0.2)
                        : Colors.blue.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: _isSilentMode ? Colors.orange : Colors.blue,
                      width: 2,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        _isSilentMode ? Icons.do_not_disturb_on : Icons.do_not_disturb_off,
                        color: _isSilentMode ? Colors.orange : Colors.blue,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        _isSilentMode ? 'Classroom Mode: ON' : 'Classroom Mode: OFF',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                          color: _isSilentMode ? Colors.orange : Colors.blue,
                        ),
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(height: 40),
                
                // Connection Info Card
                Card(
                  elevation: 4,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(20.0),
                    child: Column(
                      children: [
                        _buildInfoRow(
                          Icons.device_hub,
                          'Device Name',
                          'Classroom1',
                        ),
                        const Divider(),
                        _buildInfoRow(
                          Icons.info_outline,
                          'Status',
                          _isScanning 
                              ? 'Scanning...'
                              : _isConnected 
                                  ? 'Connected'
                                  : 'Disconnected',
                        ),
                      ],
                    ),
                  ),
                ),
                
                const Spacer(),
                
                // Action Button
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: ElevatedButton(
                    onPressed: _toggleConnection,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _isConnected ? Colors.red : Colors.blue,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      elevation: 4,
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        if (_isScanning)
                          const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                            ),
                          )
                        else
                          Icon(_isConnected ? Icons.close : Icons.bluetooth),
                        const SizedBox(width: 12),
                        Text(
                          _isScanning
                              ? 'Connecting...'
                              : _isConnected
                                  ? 'Disconnect'
                                  : 'Connect to Classroom',
                          style: const TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                
                const SizedBox(height: 20),
                
                // Favourite List Preview
                const SizedBox(height: 10),
                const Text(
                  'Favourite List:',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                const SizedBox(height: 8),
                if (_emergencyContacts.isEmpty)
                  Text('No favourite contacts added.', style: TextStyle(color: Colors.grey[600]))
                else
                  Container(
                    height: 120,
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: ListView.builder(
                      itemCount: _emergencyContacts.length,
                      itemBuilder: (context, index) {
                        final contact = _emergencyContacts[index];
                        return ListTile(
                          dense: true,
                          leading: const Icon(Icons.star, color: Colors.orange),
                          title: Text(contact.name),
                          subtitle: Text(contact.phoneNumber),
                        );
                      },
                    ),
                  ),

                const SizedBox(height: 20),
                
                // Info Text
                Text(
                  _isConnected
                      ? 'Classroom mode is ON (Priority DND). Only your emergency/starred contacts can ring.'
                      : 'Connect to Classroom1 to enable classroom mode automatically.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
  
  Widget _buildInfoRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        children: [
          Icon(icon, size: 20, color: Colors.grey[700]),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 14,
                color: Colors.grey[700],
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
