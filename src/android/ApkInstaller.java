package plugin.apkInstaller;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.BuildHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import android.content.Intent;
import android.net.Uri;
import android.content.Context;
import android.support.v4.content.FileProvider;
import java.text.SimpleDateFormat;
import android.Manifest;
import android.os.Build;
import android.provider.Settings;
import android.content.Intent;
import android.content.pm.PackageManager;


public class ApkInstaller extends CordovaPlugin {
    private static final String ACTION_INSTALL = "install";
    private static final String ACTION_PERMISSION = "permission";

    private static final int INSTALL_PERMISSION_REQUEST_CODE = 0;
    private static final int UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE = 1;
    private static final int OTHER_PERMISSIONS_REQUEST_CODE = 2;
    private static String[] OTHER_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        if (action.equals(ACTION_PERMISSION)) {
            if (verifyInstallPermission() && verifyOtherPermissions()) {
                callbackContext.success();
                return true;
            }else {
                callbackContext.error("No permission");
                return false;
            }
        }
    
        if (action.equals(ACTION_INSTALL)) {
            String fileName = data.getString(0);
            Context context = this.cordova.getActivity().getApplicationContext();
            File apkFile = new File(context.getFilesDir() + "/" + fileName);
            if (!apkFile.exists()) {
                callbackContext.error("File not found: " + fileName);
                return false;
            }
            
            try {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", apkFile);
                    Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                    i.setData(uri);
                    i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(i);
                }else {
                    String command = "chmod 666 " + apkFile.getCanonicalPath();
                    Runtime runtime = Runtime.getRuntime();
                    runtime.exec(command);

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.setDataAndType(Uri.parse("file://" + apkFile.getCanonicalPath()), "application/vnd.android.package-archive");
                    context.startActivity(i);
                }
            } catch (Exception ex) {
                callbackContext.error(ex.toString());
                return false;
            }
            
            return true;
        }
    
        callbackContext.error("No action");
        return false;
    }

    public boolean verifyInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                String applicationId = (String) BuildHelper.getBuildConfigValue(cordova.getActivity(), "APPLICATION_ID");
                Uri packageUri = Uri.parse("package:" + applicationId);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setData(packageUri);
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        else {
            try {
                if (Settings.Secure.getInt(cordova.getActivity().getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) != 1) {
                    Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                    cordova.setActivityResultCallback(this);
                    cordova.getActivity().startActivityForResult(intent, UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE);
                    return false;
                }
            }
            catch (Settings.SettingNotFoundException e) {}
        }

        return true;
    }

    // Prompt user for all other permissions if we don't already have them all.
    public boolean verifyOtherPermissions() {
        boolean hasOtherPermissions = true;
        for (String permission:OTHER_PERMISSIONS)
            hasOtherPermissions = hasOtherPermissions && cordova.hasPermission(permission);

        if (!hasOtherPermissions) {
            cordova.requestPermissions(this, OTHER_PERMISSIONS_REQUEST_CODE, OTHER_PERMISSIONS);
            return false;
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                return;
            }

            verifyOtherPermissions();
        }
        else if (requestCode == UNKNOWN_SOURCES_PERMISSION_REQUEST_CODE) {
            try {
                if (Settings.Secure.getInt(cordova.getActivity().getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS) != 1) {
                    return;
                }
            }
            catch (Settings.SettingNotFoundException e) {}

            verifyOtherPermissions();
        }
    }

    // React to user's response to our request for other permissions.
    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == OTHER_PERMISSIONS_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    return;
                }
            }
        }
    }
}
