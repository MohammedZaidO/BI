import 'package:flutter/material.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/contacts_service.dart';

class EmergencyContactsScreen extends StatefulWidget {
  const EmergencyContactsScreen({super.key});

  @override
  State<EmergencyContactsScreen> createState() => _EmergencyContactsScreenState();
}

class _EmergencyContactsScreenState extends State<EmergencyContactsScreen> {
  List<EmergencyContact> _emergencyContacts = [];
  bool _isLoading = false;
  
  @override
  void initState() {
    super.initState();
    _loadEmergencyContacts();
  }
  
  Future<void> _loadEmergencyContacts() async {
    setState(() {
      _isLoading = true;
    });
    
    final contacts = await ContactsService.getEmergencyContacts();
    setState(() {
      _emergencyContacts = contacts;
      _isLoading = false;
    });
  }
  
  Future<void> _addContactFromPhone() async {
    final status = await Permission.contacts.request();
    if (!status.isGranted) {
      _showSnackBar('Contacts permission is required', Colors.red);
      return;
    }
    
    try {
      final contacts = await FlutterContacts.getContacts(
        withProperties: true,
        withThumbnail: false,
      );
      if (contacts.isEmpty) {
        _showSnackBar('No contacts found', Colors.orange);
        return;
      }
      
      final selectedContact = await showDialog<Contact>(
        context: context,
        builder: (context) => _ContactPickerDialog(contacts: contacts),
      );
      
      if (selectedContact != null && selectedContact.phones.isNotEmpty) {
        final phone = selectedContact.phones.first.number;
        final name = selectedContact.displayName;
        
        // Ensure the contact is natively starred so Priority DND allows the call
        selectedContact.isStarred = true;
        try {
          await FlutterContacts.updateContact(selectedContact);
        } catch (e) {
          print('Failed to star contact natively: $e');
        }
        
        final emergencyContact = EmergencyContact(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          name: name,
          phoneNumber: phone,
        );
        
        final success = await ContactsService.addEmergencyContact(emergencyContact);
        if (success) {
          _loadEmergencyContacts();
          _showSnackBar('Emergency contact added', Colors.green);
        } else {
          _showSnackBar('Contact already exists', Colors.orange);
        }
      }
    } catch (e) {
      _showSnackBar('Error loading contacts: $e', Colors.red);
    }
  }
  
  Future<void> _addManualContact() async {
    final nameController = TextEditingController();
    final phoneController = TextEditingController();
    
    final result = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Add Emergency Contact'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(
                labelText: 'Name',
                hintText: 'Enter contact name',
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: phoneController,
              decoration: const InputDecoration(
                labelText: 'Phone Number',
                hintText: 'Enter phone number',
              ),
              keyboardType: TextInputType.phone,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () {
              if (nameController.text.isNotEmpty && phoneController.text.isNotEmpty) {
                Navigator.pop(context, true);
              }
            },
            child: const Text('Add'),
          ),
        ],
      ),
    );
    
    if (result == true) {
      final emergencyContact = EmergencyContact(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        name: nameController.text,
        phoneNumber: phoneController.text,
      );
      
      // Native contact integration to bypass DND
      try {
        if (await Permission.contacts.request().isGranted) {
          final newContact = Contact()
            ..name = Name(first: nameController.text)
            ..phones = [Phone(phoneController.text)]
            ..isStarred = true;
          await FlutterContacts.insertContact(newContact);
        }
      } catch (e) {
         print('Failed to insert native contact: $e');
      }
      
      final success = await ContactsService.addEmergencyContact(emergencyContact);
      if (success) {
        _loadEmergencyContacts();
        _showSnackBar('Emergency contact added', Colors.green);
      } else {
        _showSnackBar('Contact already exists', Colors.orange);
      }
    }
  }
  
  Future<void> _removeContact(EmergencyContact contact) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Remove Contact'),
        content: Text('Remove ${contact.name} from emergency contacts?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, true),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            child: const Text('Remove'),
          ),
        ],
      ),
    );
    
    if (confirm == true) {
      // Un-star native contact to restore normal DND blocking
      try {
        if (await Permission.contacts.request().isGranted) {
          final contacts = await FlutterContacts.getContacts(withProperties: true);
          final cleanPhone = contact.phoneNumber.replaceAll(RegExp(r'\D'), '');
          
          for (var c in contacts) {
            bool matches = c.phones.any((p) => 
                p.number.replaceAll(RegExp(r'\D'), '').endsWith(cleanPhone) || 
                cleanPhone.endsWith(p.number.replaceAll(RegExp(r'\D'), '')));
                
            if (matches && c.isStarred) {
              c.isStarred = false;
              await FlutterContacts.updateContact(c);
              break;
            }
          }
        }
      } catch (e) {
        print('Failed to unstar native contact: $e');
      }

      final success = await ContactsService.removeEmergencyContact(contact.id);
      if (success) {
        _loadEmergencyContacts();
        _showSnackBar('Contact removed', Colors.green);
      }
    }
  }
  
  void _showSnackBar(String message, Color color) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        duration: const Duration(seconds: 2),
      ),
    );
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Emergency Contacts'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _emergencyContacts.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        Icons.contacts_outlined,
                        size: 80,
                        color: Colors.grey[400],
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'No emergency contacts',
                        style: TextStyle(
                          fontSize: 18,
                          color: Colors.grey[600],
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Add contacts that can call you\nwhen phone is in silent mode',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 14,
                          color: Colors.grey[500],
                        ),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: _emergencyContacts.length,
                  itemBuilder: (context, index) {
                    final contact = _emergencyContacts[index];
                    return Card(
                      margin: const EdgeInsets.only(bottom: 12),
                      elevation: 2,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: Colors.red,
                          child: Text(
                            contact.name[0].toUpperCase(),
                            style: const TextStyle(color: Colors.white),
                          ),
                        ),
                        title: Text(
                          contact.name,
                          style: const TextStyle(fontWeight: FontWeight.bold),
                        ),
                        subtitle: Text(contact.phoneNumber),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete, color: Colors.red),
                          onPressed: () => _removeContact(contact),
                        ),
                      ),
                    );
                  },
                ),
      floatingActionButton: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          FloatingActionButton.extended(
            onPressed: _addContactFromPhone,
            icon: const Icon(Icons.contacts),
            label: const Text('From Phone'),
            backgroundColor: Colors.blue,
          ),
          const SizedBox(height: 12),
          FloatingActionButton.extended(
            onPressed: _addManualContact,
            icon: const Icon(Icons.person_add),
            label: const Text('Manual'),
            backgroundColor: Colors.green,
          ),
        ],
      ),
    );
  }
}

class _ContactPickerDialog extends StatelessWidget {
  final List<Contact> contacts;
  
  const _ContactPickerDialog({required this.contacts});
  
  @override
  Widget build(BuildContext context) {
    final contactsWithPhones = contacts.where((c) => c.phones.isNotEmpty).toList();
    
    return Dialog(
      child: Container(
        width: double.maxFinite,
        height: 500,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(16.0),
              child: Row(
                children: [
                  const Text(
                    'Select Contact',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  const Spacer(),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: contactsWithPhones.length,
                itemBuilder: (context, index) {
                  final contact = contactsWithPhones[index];
                  
                  return ListTile(
                    leading: CircleAvatar(
                      child: Text(
                        contact.displayName.isNotEmpty 
                            ? contact.displayName[0].toUpperCase()
                            : '?',
                      ),
                    ),
                    title: Text(contact.displayName.isNotEmpty 
                        ? contact.displayName 
                        : 'Unknown'),
                    subtitle: Text(contact.phones.first.number),
                    onTap: () => Navigator.pop(context, contact),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
