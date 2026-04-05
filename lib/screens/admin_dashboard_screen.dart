import 'package:flutter/material.dart';
import '../services/bluetooth_service.dart';

class AdminDashboardScreen extends StatefulWidget {
  const AdminDashboardScreen({super.key});

  @override
  State<AdminDashboardScreen> createState() => _AdminDashboardScreenState();
}

class _AdminDashboardScreenState extends State<AdminDashboardScreen> {
  final BluetoothService _bluetoothService = BluetoothService();
  final TextEditingController _passwordController = TextEditingController();
  bool _isAuthenticated = false;
  bool _isSending = false;

  bool _isLoadingClients = false;
  List<String> _connectedClients = [];

  void _login() {
    if (_passwordController.text == 'admin123') { // Simple hardcoded password
      setState(() {
        _isAuthenticated = true;
      });
      _fetchClients();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Incorrect Password')),
      );
    }
  }

  Future<void> _fetchClients() async {
    setState(() {
      _isLoadingClients = true;
    });
    
    // Attempt to connect if not already connected
    if (!_bluetoothService.isConnected) {
      await _bluetoothService.connect();
    }
    
    final clients = await _bluetoothService.getConnectedClients();
    if (mounted) {
      setState(() {
        _connectedClients = clients.where((mac) => mac.trim() != "NONE").toList();
        _isLoadingClients = false;
      });
    }
  }

  Future<void> _disconnectIndividualClient(String mac) async {
    final success = await _bluetoothService.sendAdminCommand('DISCONNECT_$mac');
    if (success) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Disconnect sent for MAC: $mac')),
        );
      }
      // Refresh list after brief delay
      Future.delayed(const Duration(seconds: 2), _fetchClients);
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to send targeted disconnect command.')),
        );
      }
    }
  }

  Future<void> _disconnectAllStudents() async {
    setState(() {
      _isSending = true;
    });
    
    // Attempt to connect if not already connected
    if (!_bluetoothService.isConnected) {
      await _bluetoothService.connect();
    }
    
    // Send command
    final success = await _bluetoothService.sendAdminCommand('DISCONNECT_ALL');
    
    setState(() {
      _isSending = false;
    });

    if (success) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Disconnect command sent successfully!')),
        );
      }
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to send command. Ensure device is near.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_isAuthenticated) {
      return Scaffold(
        appBar: AppBar(title: const Text('Admin Login')),
        body: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.admin_panel_settings, size: 80, color: Colors.blue),
              const SizedBox(height: 24),
              TextField(
                controller: _passwordController,
                obscureText: true,
                decoration: const InputDecoration(
                  labelText: 'Admin Password',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: _login,
                child: const Text('Login'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Admin Dashboard'),
        backgroundColor: Colors.red[800],
        foregroundColor: Colors.white,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.warning_rounded, size: 80, color: Colors.orange),
              const SizedBox(height: 24),
              const Text(
                'Classroom Controls',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 16),
              const Text(
                'This will forcefully drop all connected student phones from the Classroom ESP32 device.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 16),
              ),
              const SizedBox(height: 24),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                   const Text(
                    'Connected Devices',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    onPressed: _isLoadingClients ? null : _fetchClients,
                  ),
                ],
              ),
              const Divider(),
              Expanded(
                child: _isLoadingClients 
                  ? const Center(child: CircularProgressIndicator())
                  : _connectedClients.isEmpty
                    ? const Center(child: Text('No devices currently connected.'))
                    : ListView.builder(
                        itemCount: _connectedClients.length,
                        itemBuilder: (context, index) {
                          final mac = _connectedClients[index];
                          // Create a slightly more friendly name
                          final shortMac = mac.length >= 5 ? mac.substring(mac.length - 5) : mac;
                          return Card(
                            margin: const EdgeInsets.symmetric(vertical: 4),
                            child: ListTile(
                              leading: const Icon(Icons.phonelink),
                              title: Text('Student Device ($shortMac)', style: const TextStyle(fontWeight: FontWeight.bold)),
                              subtitle: Text('ID: $mac', style: const TextStyle(fontSize: 12)),
                              trailing: ElevatedButton(
                                onPressed: () {
                                  // Optimistically remove from UI to feel fast
                                  setState(() {
                                    _connectedClients.removeAt(index);
                                  });
                                  _disconnectIndividualClient(mac);
                                },
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.orange,
                                  foregroundColor: Colors.white,
                                ),
                                child: const Text('Kick'),
                              ),
                            ),
                          );
                        },
                      ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                height: 56,
                child: ElevatedButton.icon(
                  onPressed: _isSending ? null : _disconnectAllStudents,
                  icon: _isSending 
                      ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                      : const Icon(Icons.phonelink_erase),
                  label: const Text('Remove All Connected Phones'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
