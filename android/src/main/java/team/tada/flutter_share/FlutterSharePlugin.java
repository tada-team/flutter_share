package team.tada.flutter_share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class FlutterSharePlugin implements MethodChannel.MethodCallHandler, PluginRegistry.NewIntentListener {

    private static final String TAG = "FlutterSharePlugin";
    private static final String TITLE = "title";
    private static final String TEXT = "text";
    private static final String AUTHORITY = "authority";
    private static final String PATH = "path";
    private static final String TYPE = "type";
    private static final String IS_MULTIPLE = "is_multiple";
    private static final String CHANNEL = "plugins.flutter.io/share";

    private final Registrar mRegistrar;
    private final MethodChannel methodChannel;

    private FlutterSharePlugin(Registrar registrar, MethodChannel methodChannel) {
        this.mRegistrar = registrar;
        this.methodChannel = methodChannel;
    }

    public static void registerWith(Registrar registrar) {
        MethodChannel methodChannel = new MethodChannel(registrar.messenger(), CHANNEL);

        FlutterSharePlugin plugin = new FlutterSharePlugin(registrar, methodChannel);

        registrar.addNewIntentListener(plugin);

        methodChannel.setMethodCallHandler(plugin);
    }

    @Override
    public void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (call.method.equals("configure")) {
            Log.i(TAG, "called configure");

            if (mRegistrar.activity() != null) {
                Intent intent = mRegistrar.activity().getIntent();
                if (intent != null && intent.getAction() != null) {
                    String intentAction = intent.getAction();

                    Log.i(TAG, "ACTION - " + intentAction);

                    if (intentAction.equals("android.intent.action.SEND") || intentAction.equals("android.intent.action.SEND_MULTIPLE")) {
                        processShareIntent(intent);
                    }
                }
            }

            result.success(null);
            return;
        }

        if (call.method.equals("share")) {
            if (!(call.arguments instanceof Map)) {
                throw new IllegalArgumentException("Map argument expected");
            }
            // Android does not support showing the share sheet at a particular point on screen.
            if (call.argument(IS_MULTIPLE)) {
                ArrayList<Uri> dataList = new ArrayList<>();
                for (int i = 0; call.hasArgument(Integer.toString(i)); i++) {
                    dataList.add(Uri.parse((String) call.argument(Integer.toString(i))));
                }
                shareMultiple(dataList, (String) call.argument(TYPE), call.hasArgument(TITLE) ? (String) call.argument(TITLE) : "");
            } else {
                ShareType shareType = ShareType.fromMimeType((String) call.argument(TYPE));

                String authority = "";
                if (call.hasArgument(AUTHORITY)) {
                    authority = call.argument(AUTHORITY);
                }

                if (ShareType.TYPE_PLAIN_TEXT.equals(shareType)) {
                    share((String) call.argument(TEXT), shareType, call.hasArgument(TITLE) ? (String) call.argument(TITLE) : "");
                } else {
                    share((String) call.argument(PATH), (call.hasArgument(TEXT) ? (String) call.argument(TEXT) : ""), shareType, (call.hasArgument(TITLE) ? (String) call.argument(TITLE) : ""), authority);
                }
            }

            result.success(null);
            return;
        }

        Log.i(TAG, "called non implemented method - " + call.method);
        result.notImplemented();
    }

    private void share(String text, ShareType shareType, String title) {
        share("", text, shareType, title, "");
    }

    private void share(String path, String text, ShareType shareType, String title, String authority) {
        if (!ShareType.TYPE_PLAIN_TEXT.equals(shareType) && (path == null || path.isEmpty())) {
            throw new IllegalArgumentException("Non-empty path expected");
        } else if (ShareType.TYPE_PLAIN_TEXT.equals(shareType) && (text == null || text.isEmpty())) {
            throw new IllegalArgumentException("Non-empty text expected");
        }
        if (shareType == null) {
            throw new IllegalArgumentException("Non-empty mimeType expected");
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);

        if (!TextUtils.isEmpty(title)) {
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        }
        if (!ShareType.TYPE_PLAIN_TEXT.equals(shareType)) {
            Log.i(TAG, "path: " + path + ", authority: " + authority);

            Uri uri;
            if (authority.isEmpty()) {
                uri = Uri.parse(path);
            } else {
                Context context = mRegistrar.context();

                File file = new File(path);
                uri = FileProvider.getUriForFile(context, authority, file);

                List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(shareIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }

            Log.i(TAG, "uri path: " + uri.toString());

            shareIntent.setType(shareType.toString());

            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (!TextUtils.isEmpty(text)) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            }
        } else {
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            shareIntent.setType(shareType.toString());
        }

        Intent chooserIntent = Intent.createChooser(shareIntent, null /* dialog title optional */);
        if (mRegistrar.activity() != null) {
            mRegistrar.activity().startActivity(chooserIntent);
        } else {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mRegistrar.context().startActivity(chooserIntent);
        }
    }

    private void shareMultiple(ArrayList<Uri> dataList, String mimeType, String title) {
        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("Non-empty data expected");
        }
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException("Non-empty mimeType expected");
        }

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND_MULTIPLE);

        if (!TextUtils.isEmpty(title)) {
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
        }

        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, dataList);
        shareIntent.setType(mimeType);

        Intent chooserIntent = Intent.createChooser(shareIntent, null /* dialog title optional */);
        if (mRegistrar.activity() != null) {
            mRegistrar.activity().startActivity(chooserIntent);
        } else {
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mRegistrar.context().startActivity(chooserIntent);
        }
    }

    private boolean processShareIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        Map<String, String> params = new HashMap<>();

        if (Intent.ACTION_SEND.equals(action) && type != null) {

            // In some cases files are shared with type == text/plain so this check corrects type
            Uri sharedUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (sharedUri != null && "text/plain".equals(type)) {
                type = "*/*";
            }

            if ("text/plain".equals(type)) {
                String sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                Log.i(TAG, "receiving shared title: " + sharedTitle);

                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                Log.i(TAG, "receiving shared text: " + sharedText);

                params.put(TYPE, type);
                params.put(TEXT, sharedText);

                if (!TextUtils.isEmpty(sharedTitle)) {
                    params.put(TITLE, sharedTitle);
                }
            } else {
                String sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                Log.i(TAG, "receiving shared title: " + sharedTitle);

                Log.i(TAG, "receiving shared file: " + sharedUri);

                if (sharedTitle == null || sharedTitle.isEmpty()) {
                    sharedTitle = this.getFileName(mRegistrar.context(), sharedUri);
                }

                String path = FileHelper.getPath(mRegistrar.context(), sharedUri);
                if (path != null) {
                    params.put(TYPE, type);
                    params.put(PATH, path);

                    if (!TextUtils.isEmpty(sharedTitle)) {
                        params.put(TITLE, sharedTitle);
                    }

                    if (!intent.hasExtra(Intent.EXTRA_TEXT)) {
                        params.put(TEXT, intent.getStringExtra(Intent.EXTRA_TEXT));
                    }
                }

            }

        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            Log.i(TAG, "receiving shared files!");

            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);

            params.put(TYPE, type);
            params.put(IS_MULTIPLE, "true");

            for (int i = 0; i < uris.size(); i++) {
                Uri sharedUri = uris.get(i);
                String path = FileHelper.getPath(mRegistrar.context(), sharedUri);

                if (path != null) params.put(Integer.toString(i), path);
            }
        }

        Log.i(TAG, params.toString());

        methodChannel.invokeMethod("onReceive", params);

        return false;
    }

    private boolean isVirtualFile(Context context, Uri uri) {
        final String authority = uri.getAuthority();
        if ("com.google.android.apps.docs.storage.legacy".equals(authority)) {
            return true;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false;
        }

        if (!DocumentsContract.isDocumentUri(context, uri)) {
            return false;
        }

        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[] { DocumentsContract.Document.COLUMN_FLAGS },
                null, null, null);
        if (cursor == null) return false;

        int flags = 0;
        if (cursor.moveToFirst()) {
            flags = cursor.getInt(0);
        }
        cursor.close();

        return (flags & DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT) != 0;
    }

    private String getFileName(Context context, Uri uri) {
        if (uri == null || !this.isVirtualFile(context, uri)) return null;

        String result = null;
        if ("content".equals(uri.getScheme())) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                return null;
            }

            try {
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    @Override
    public boolean onNewIntent(Intent intent) {
        return processShareIntent(intent);
    }

    public enum ShareType {
        TYPE_PLAIN_TEXT("text/plain"),
        TYPE_IMAGE("image/*"),
        TYPE_FILE("*/*");

        String mimeType;

        ShareType(String mimeType) {
            this.mimeType = mimeType;
        }

        static ShareType fromMimeType(String mimeType) {
            for (ShareType shareType : values()) {
                if (shareType.mimeType.equals(mimeType)) {
                    return shareType;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return mimeType;
        }
    }
}
