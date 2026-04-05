import 'package:flutter/services.dart';

class PhoneService {
  static const MethodChannel _channel = MethodChannel('phone_service');
  
  static bool _isClassroomModeEnabled = false;
  
  /// Opens system settings screen if DND access isn't granted yet.
  static Future<bool> ensureDndAccess() async {
    try {
      final result = await _channel.invokeMethod('ensureDndAccess');
      return result == true;
    } catch (e) {
      print('Error ensuring DND access: $e');
      return false;
    }
  }
  
  /// Enables Priority DND (required for: only emergency/starred contacts ring).
  static Future<bool> enableClassroomMode() async {
    try {
      final result = await _channel.invokeMethod('enablePriorityDnd');
      _isClassroomModeEnabled = result == true;
      return _isClassroomModeEnabled;
    } catch (e) {
      print('Error enabling classroom mode: $e');
      return false;
    }
  }
  
  static Future<bool> disableClassroomMode() async {
    try {
      final result = await _channel.invokeMethod('disableDnd');
      _isClassroomModeEnabled = false;
      return result == true;
    } catch (e) {
      print('Error disabling classroom mode: $e');
      return false;
    }
  }
  
  static Future<bool> isClassroomModeEnabled() async {
    try {
      final result = await _channel.invokeMethod('isPriorityDndEnabled');
      _isClassroomModeEnabled = result == true;
      return _isClassroomModeEnabled;
    } catch (e) {
      return false;
    }
  }
  
  static bool get isClassroomMode => _isClassroomModeEnabled;
}
