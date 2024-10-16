package com.pchab.android.plugin;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.HashMap;

public class Downloader extends CordovaPlugin {

    public static final String ACTION_DOWNLOAD = "download";

    private static final String TAG = "DownloaderPlugin";

    private Activity cordovaActivity;
    private DownloadManager downloadManager;
    private HashMap<Long, Download> downloadMap;

    @Override
    protected void pluginInitialize()
    {
        Log.d(TAG, "PluginInitialize");

        cordovaActivity = this.cordova.getActivity();

        downloadManager = (DownloadManager) cordovaActivity.getSystemService(Context.DOWNLOAD_SERVICE);
        downloadMap = new HashMap<>();
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        // Register receiver for Notification actions
        if (Build.VERSION.SDK_INT >= 34) {
            cordovaActivity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            cordovaActivity.registerReceiver(downloadReceiver, filter);
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "CordovaPlugin: execute " + action);

        if (ACTION_DOWNLOAD.equals(action)) {

            Log.d(TAG, "CordovaPlugin: load " + action);
            return download(args, callbackContext);

        }

        return false;


    }

    private boolean download(JSONArray args, CallbackContext callbackContext)
    {
        Log.d(TAG, "CordovaPlugin: " + ACTION_DOWNLOAD);

        try {

            JSONObject arg_object = args.getJSONObject(0);
            Uri uri = Uri.parse(arg_object.getString("url"));
            JSONObject headers = arg_object.getJSONObject("headers");
            String path = arg_object.getString("path");
            String description = arg_object.getString("description");

            Download mDownload = new Download(path, callbackContext);

            DownloadManager.Request request = new DownloadManager.Request(uri);
            //Restrict the types of networks over which this download may proceed.
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            //Set whether this download may proceed over a roaming connection.
            request.setAllowedOverRoaming(true);
            //Set the title of this download, to be displayed in notifications (if enabled).
            request.setTitle(path);
            //Set a description of this download, to be displayed in notifications (if enabled)
            if (description != null) {
                request.setDescription(description);
            }
            //Set the local destination for the downloaded file to a path within the application's external public directory
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, path);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            JSONArray names = headers.names();
            for( int i = 0; i < names.length(); i++ ) {
                String key = names.getString( i );
                request.addRequestHeader( key, headers.getString( key ) );
            }

            // save the download
            downloadMap.put(downloadManager.enqueue(request), mDownload);

        return true;

        } catch (Exception e) {

            System.err.println("Exception: " + e.getMessage());
            callbackContext.error(e.getMessage());

            return false;
        }
    }

    private BroadcastReceiver downloadReceiver = new DownloadBroadcastReceiver();

    private class Download {
        public String path;
        public CallbackContext callbackContext;

        public Download(String path, CallbackContext callbackContext) {
            this.path = path;
            this.callbackContext = callbackContext;
        }
    }

  private class DownloadBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive( Context context, Intent intent) {

        DownloadManager.Query query = new DownloadManager.Query();
        Long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
        query.setFilterById(downloadId);
        Cursor cursor = downloadManager.query(query);

        if (cursor.moveToFirst()){

            //Retrieve the saved download
            Download currentDownload = downloadMap.get(downloadId);
            downloadMap.remove(downloadId);

            int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(columnIndex);
            int columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
            int reason = cursor.getInt(columnReason);

            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    try {
                        currentDownload.callbackContext.success( Environment.DIRECTORY_DOWNLOADS);
                    } catch (Exception e) {
                        System.err.println("Exception: " + e.getMessage());
                        currentDownload.callbackContext.error(e.getMessage());
                    }
                    break;
                case DownloadManager.STATUS_FAILED:
                    currentDownload.callbackContext.error(reason);
                    break;
                case DownloadManager.STATUS_PAUSED:
                case DownloadManager.STATUS_PENDING:
                case DownloadManager.STATUS_RUNNING:
                default:
                    break;
            }
        }
    }

  }
}
