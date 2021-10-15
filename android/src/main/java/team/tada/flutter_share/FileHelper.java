/*
 * Copyright (C) 2007-2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package team.tada.flutter_share;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class FileHelper {

  FileHelper() {
  }

  static String getPath(final Context context, final Uri uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      return getPathNew(context, uri);
    }
    return getPathOld(context, uri);
  }

  /**
   * Get a file path from a Uri. This will get the the path for Storage Access Framework Documents,
   * as well as the _data field for the MediaStore and other file-based ContentProviders.<br>
   * <br>
   * Callers should check whether the path is local before assuming it represents a local file.
   *
   * @param context The context.
   * @param uri The Uri to query.
   * @author paulburke
   */
  @TargetApi(Build.VERSION_CODES.KITKAT)
  private static String getPathNew(final Context context, final Uri uri) {
    if (DocumentsContract.isDocumentUri(context, uri)) {
      return getDocumentUri(context, uri);
    } else if ("content".equalsIgnoreCase(uri.getScheme())) {

      // Return the remote address
      if (isGooglePhotosUri(uri)) {
        return uri.getLastPathSegment();
      }

      return getFilePathFromURI(context, uri);
    }
    // File
    else if ("file".equalsIgnoreCase(uri.getScheme())) {
      return uri.getPath();
    }

    return null;
  }

  private static String getFilePathFromURI(Context context, Uri contentUri) {
    String fileName = getFileName(contentUri);

    File rootDataDir = context.getFilesDir();

    if (!TextUtils.isEmpty(fileName)) {
      String fileExtension = getExtension(context, contentUri);

      if (fileExtension != null) {
        fileName = fileName + "." + fileExtension;
      }

      File file = new File(rootDataDir + File.separator + fileName);
      copy(context, contentUri, file);


      return file.getAbsolutePath();
    }

    return null;
  }

  private static String getFileName(Uri uri) {
    if (uri == null) {
      return null;
    }
    String fileName = null;
    String path = uri.getPath();
    int cut = path.lastIndexOf('/');
    if (cut != -1) {
      fileName = path.substring(cut + 1);
    }
    return fileName;
  }

  private static String getExtension(Context context, Uri uri) {
    String extension;

    String file = uri.getLastPathSegment();
    if (file == null || (file.contains(".") && !file.substring(file.indexOf(".")).isEmpty())) {
      return null;
    }

    if (uri.getScheme() == null) {
      return null;
    }

    if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
      final MimeTypeMap mime = MimeTypeMap.getSingleton();
      extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
    } else {
      extension = MimeTypeMap
          .getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
    }

    return extension;
  }

  private static void copy(Context context, Uri srcUri, File dstFile) {
    try {
      InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
      if (inputStream == null) {
        return;
      }
      OutputStream outputStream = new FileOutputStream(dstFile);

      copyData(inputStream, outputStream);
      inputStream.close();
      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void copyData(InputStream in, OutputStream out) throws Exception {
    byte[] buffer = new byte[8 * 1024];
    int len;
    while ((len = in.read(buffer)) > 0) {
      out.write(buffer, 0, len);
    }
  }

  private static String getPathOld(final Context context, final Uri uri) {
    if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
      if (isGooglePhotosUri(uri)) {
        return uri.getLastPathSegment();
      }

      return getDataColumn(context, uri, null, null);
    } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
      return uri.getPath();
    }

    return null;
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private static String getDocumentUri(final Context context, final Uri uri) {
    if (DocumentsContract.isDocumentUri(context, uri)) {

      if (isExternalStorageDocument(uri)) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        if ("primary".equalsIgnoreCase(type)) {
          return Environment.getExternalStorageDirectory() + "/" + split[1];
        }

        // TODO handle non-primary volumes
      }
      // DownloadsProvider
      else if (isDownloadsDocument(uri)) {

        final String id = DocumentsContract.getDocumentId(uri);
        final Uri contentUri = ContentUris.withAppendedId(
            Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

        return getDataColumn(context, contentUri, null, null);
      }
      // MediaProvider
      else if (isMediaDocument(uri)) {
        final String docId = DocumentsContract.getDocumentId(uri);
        final String[] split = docId.split(":");
        final String type = split[0];

        Uri contentUri = null;
        if ("image".equals(type)) {
          contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        } else if ("video".equals(type)) {
          contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        } else if ("audio".equals(type)) {
          contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        final String selection = "_id=?";
        final String[] selectionArgs = new String[]{
            split[1]
        };

        return getDataColumn(context, contentUri, selection, selectionArgs);
      }
    }

    return null;
  }

  private static String getDataColumn(Context context, Uri uri, String selection,
      String[] selectionArgs) {
    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = {
        column
    };

    try {
      cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
          null);
      if (cursor != null && cursor.moveToFirst()) {
        final int index = cursor.getColumnIndexOrThrow(column);
        return cursor.getString(index);
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return null;
  }


  /**
   * @param uri The Uri to check.
   * @return Whether the Uri authority is ExternalStorageProvider.
   */
  private static boolean isExternalStorageDocument(Uri uri) {
    return "com.android.externalstorage.documents".equals(uri.getAuthority());
  }

  /**
   * @param uri The Uri to check.
   * @return Whether the Uri authority is DownloadsProvider.
   */
  private static boolean isDownloadsDocument(Uri uri) {
    return "com.android.providers.downloads.documents".equals(uri.getAuthority());
  }

  /**
   * @param uri The Uri to check.
   * @return Whether the Uri authority is MediaProvider.
   */
  private static boolean isMediaDocument(Uri uri) {
    return "com.android.providers.media.documents".equals(uri.getAuthority());
  }

  /**
   * @param uri The Uri to check.
   * @return Whether the Uri authority is Google Photos.
   */
  private static boolean isGooglePhotosUri(Uri uri) {
    return "com.google.android.apps.photos.content".equals(uri.getAuthority());
  }
}
