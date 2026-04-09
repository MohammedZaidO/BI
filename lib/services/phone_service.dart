import 'package:flutter/services.dart';

class PhoneService {
  static const MethodChannel _channel = MethodChannel('phone_service');
  
  static bool _isClassroomModeEnabled = false;
  
  /// Consolidated permissions for Classroom Mode:
  /// 1. DND Access (for silencing)
  /// 2. READ_CONTACTS (for screening service to see phonebook)
  /// 3. POST_NOTIFICATIONS (for the Emergency Alert shout)
  static Future<bool> ensurePermissions() async {
    try {
      final result = await _channel.invokeMethod('ensurePermissions');
      return result == true;
    } catch (e) {
      print('Error ensuring permissions: $e');
      return false;
    }
  }
  
  static Future<bool> enableClassroomMode() async {
    try {
      final result = await _channel.invokeMethod('enableClassroomMode');
      _isClassroomModeEnabled = result == true;
      return _isClassroomModeEnabled;
    } catch (e) {
      print('Error enabling classroom mode: $e');
      return false;
    }
  }
  
  static Future<bool> disableClassroomMode() async {
    try {
      final result = await _channel.invokeMethod('disableClassroomMode');
      _isClassroomModeEnabled = false;
      return result == true;
    } catch (e) {
      print('Error disabling classroom mode: $e');
      return false;
    }
  }
  
  static Future<bool> isClassroomModeEnabled() async {
    try {
      final result = await _channel.invokeMethod('isClassroomModeEnabled');
      _isClassroomModeEnabled = result == true;
      return _isClassroomModeEnabled;
    } catch (e) {
      return false;
    }
  }
  
  static bool get isClassroomMode => _isClassroomModeEnabled;
}
