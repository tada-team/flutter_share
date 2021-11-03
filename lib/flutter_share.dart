import 'package:flutter/services.dart';
import 'package:meta/meta.dart' show visibleForTesting;

class ShareType {
  static const ShareType TYPE_PLAIN_TEXT = const ShareType._internal("text/plain");
  static const ShareType TYPE_IMAGE = const ShareType._internal("image/*");
  static const ShareType TYPE_FILE = const ShareType._internal("*/*");

  final String _type;

  const ShareType._internal(this._type);

  static ShareType fromMimeType(String? mimeType) {
    if (mimeType == TYPE_PLAIN_TEXT.toString()) return TYPE_PLAIN_TEXT;
    if (mimeType!.contains("image")) return TYPE_IMAGE;
    return TYPE_FILE;
  }

  @override
  String toString() {
    return _type;
  }
}

/// Plugin for summoning a platform share sheet.
class Share {
  static const String TITLE = "title";
  static const String TEXT = "text";
  static const String PATH = "path";
  static const String TYPE = "type";
  static const String IS_MULTIPLE = "is_multiple";

  final ShareType? mimeType;
  final String? title;
  final String? text;
  final String path;
  final String? authority;
  final bool isErasingRequired;
  final List<Share> shares;

  Share.nullType()
      : this.mimeType = null,
        this.title = '',
        this.text = '',
        this.path = '',
        this.authority = '',
        this.isErasingRequired = false,
        this.shares = const [];

  const Share.plainText({this.title, required String this.text})
      : this.mimeType = ShareType.TYPE_PLAIN_TEXT,
        this.path = '',
        this.authority = '',
        this.isErasingRequired = false,
        this.shares = const [];

  const Share.file({
    ShareType this.mimeType = ShareType.TYPE_FILE,
    this.title,
    required this.path,
    this.authority,
    this.text = '',
    this.isErasingRequired = false,
  }) : this.shares = const [];

  const Share.image({
    ShareType this.mimeType = ShareType.TYPE_IMAGE,
    this.title,
    required this.path,
    this.authority,
    this.text = '',
    this.isErasingRequired = false,
  }) : this.shares = const [];

  const Share.multiple({
    ShareType this.mimeType = ShareType.TYPE_FILE,
    this.title,
    required this.shares,
    this.authority,
    this.isErasingRequired = false,
  })  : this.text = '',
        this.path = '';

  static Share fromReceived(Map received) {
    assert(received.containsKey(TYPE));

    ShareType type = ShareType.fromMimeType(received[TYPE]);
    if (received.containsKey(IS_MULTIPLE)) {
      List<Share> receivedShares = [];
      for (var i = 0; i < received.length - 2; i++) {
        receivedShares.add(Share.file(path: received["$i"]));
      }
      if (received.containsKey(TITLE)) {
        return Share.multiple(mimeType: type, title: received[TITLE], shares: receivedShares);
      } else {
        return Share.multiple(mimeType: type, shares: receivedShares);
      }
    } else {
      return _fromReceivedSingle(received, type);
    }
  }

  static Share _fromReceivedSingle(Map received, ShareType type) {
    switch (type) {
      case ShareType.TYPE_PLAIN_TEXT:
        if (received.containsKey(TITLE)) {
          return Share.plainText(title: received[TITLE], text: received[TEXT]);
        } else {
          return Share.plainText(text: received[TEXT]);
        }
      case ShareType.TYPE_IMAGE:
        if (received.containsKey(TITLE)) {
          if (received.containsKey(TEXT)) {
            return Share.image(path: received[PATH], title: received[TITLE], text: received[TEXT]);
          }

          return Share.image(path: received[PATH], text: received[TITLE]);
        } else {
          return Share.image(path: received[PATH]);
        }
      case ShareType.TYPE_FILE:
        if (received.containsKey(TITLE)) {
          if (received.containsKey(TEXT)) {
            return Share.file(path: received[PATH], title: received[TITLE], text: received[TEXT]);
          }

          return Share.file(path: received[PATH], text: received[TITLE]);
        } else {
          return Share.file(path: received[PATH]);
        }
      default:
        throw Exception("Unknown share type: $type");
    }
  }

  /// [MethodChannel] used to communicate with the platform side.
  @visibleForTesting
  static const MethodChannel channel = const MethodChannel('plugins.flutter.io/share');

  bool get isNull => this.mimeType == null;

  bool get isMultiple => this.shares.isNotEmpty;

  @override
  String toString() {
    // ignore: prefer_interpolation_to_compose_strings
    return 'Share{' +
        (this.isNull ? 'null }' : 'mimeType: $mimeType, title: $title, text: $text, path: $path, shares: $shares}');
  }
}
