import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'package:flutter_contacts/flutter_contacts.dart';

class EmergencyContact {
  final String id;
  final String name;
  final String phoneNumber;
  
  EmergencyContact({
    required this.id,
    required this.name,
    required this.phoneNumber,
  });
  
  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'phoneNumber': phoneNumber,
  };
  
  factory EmergencyContact.fromJson(Map<String, dynamic> json) => EmergencyContact(
    id: json['id'],
    name: json['name'],
    phoneNumber: json['phoneNumber'],
  );
}

class ContactsService {
  static const String _emergencyContactsKey = 'emergency_contacts';
  
  static Future<List<EmergencyContact>> getEmergencyContacts() async {
    final prefs = await SharedPreferences.getInstance();
    final contactsJson = prefs.getString(_emergencyContactsKey);
    
    if (contactsJson == null) {
      return [];
    }
    
    try {
      final List<dynamic> contactsList = json.decode(contactsJson);
      return contactsList
          .map((contact) => EmergencyContact.fromJson(contact))
          .toList();
    } catch (e) {
      print('Error loading emergency contacts: $e');
      return [];
    }
  }
  
  static Future<bool> addEmergencyContact(EmergencyContact contact) async {
    final contacts = await getEmergencyContacts();
    
    if (contacts.any((c) => c.id == contact.id)) {
      return false;
    }
    
    contacts.add(contact);
    return await _saveEmergencyContacts(contacts);
  }
  
  static Future<bool> removeEmergencyContact(String contactId) async {
    final contacts = await getEmergencyContacts();
    contacts.removeWhere((c) => c.id == contactId);
    return await _saveEmergencyContacts(contacts);
  }
  
  static Future<bool> _saveEmergencyContacts(List<EmergencyContact> contacts) async {
    final prefs = await SharedPreferences.getInstance();
    final contactsJson = json.encode(
      contacts.map((contact) => contact.toJson()).toList(),
    );
    return await prefs.setString(_emergencyContactsKey, contactsJson);
  }
  
  static Future<bool> isEmergencyContact(String phoneNumber) async {
    final contacts = await getEmergencyContacts();
    return contacts.any((contact) => 
      contact.phoneNumber.replaceAll(RegExp(r'[^\d]'), '') == 
      phoneNumber.replaceAll(RegExp(r'[^\d]'), ''));
  }

  /// Synchronizes all app emergency contacts with the system's 'Starred' contacts.
  /// This ensures that the DND Priority model correctly identifies emergency callers.
  static Future<void> syncAllToSystem() async {
    try {
      final appContacts = await getEmergencyContacts();
      final sysContacts = await FlutterContacts.getContacts(withProperties: true);
      
      for (var appC in appContacts) {
        final cleanAppPhone = appC.phoneNumber.replaceAll(RegExp(r'\D'), '');
        
        for (var sysC in sysContacts) {
          bool matches = sysC.phones.any((p) => 
              p.number.replaceAll(RegExp(r'\D'), '').endsWith(cleanAppPhone) || 
              cleanAppPhone.endsWith(p.number.replaceAll(RegExp(r'\D'), '')));
              
          if (matches && !sysC.isStarred) {
            sysC.isStarred = true;
            await FlutterContacts.updateContact(sysC);
          }
        }
      }
    } catch (e) {
      print('Sync failed: $e');
    }
  }
}
