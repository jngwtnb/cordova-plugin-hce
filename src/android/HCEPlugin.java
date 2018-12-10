// Cordova HCE Plugin
// (c) 2015 Don Coleman

package com.megster.cordova.hce;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONArray;

import java.util.Arrays;

public class HCEPlugin extends CordovaPlugin {

    private static final String REGISTER_COMMAND_CALLBACK = "registerCommandCallback";
    private static final String SEND_RESPONSE = "sendResponse";
    private static final String REGISTER_DEACTIVATED_CALLBACK = "registerDeactivatedCallback";
    private static final String SET_NDEF_MESSAGE = "setNdefMessage";
    private static final String TAG = "HCEPlugin";

    private CallbackContext onCommandCallback;
    private CallbackContext onDeactivatedCallback;

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, action);

        if (action.equalsIgnoreCase(REGISTER_COMMAND_CALLBACK)) {

            // TODO this would be better in an initializer
            CordovaApduService.setHCEPlugin(this);

            // save the callback`
            onCommandCallback = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equalsIgnoreCase(SEND_RESPONSE)) {

            byte[] data = args.getArrayBuffer(0);

            if (CordovaApduService.sendResponse(data)) {
                callbackContext.success();
            } else {
                // TODO This message won't make sense to developers.
                callbackContext.error("Missing Reference to CordovaApduService.");
            }

        } else if (action.equalsIgnoreCase(REGISTER_DEACTIVATED_CALLBACK)) {

            // save the callback`
            onDeactivatedCallback = callbackContext;
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);

        } else if (action.equalsIgnoreCase(SET_NDEF_MESSAGE)) {
            CordovaApduService.setHCEPlugin(this);

            JSONArray jsonArray = args.getJSONArray(0);
            int jsonArrayLength = jsonArray.length();

            byte[] bytes = new byte[jsonArrayLength];
            for (int i=0; i<jsonArrayLength; i++) {
                bytes[i] = (byte)(((int)jsonArray.get(i)) & 0xFF);
            }
            CordovaApduService.ndefMessage = bytes;

            callbackContext.success();

        } else {

            return false;

        }

        return true;
    }

    public void deactivated(int reason) {
        Log.d(TAG, "deactivated " + reason);
        if (onDeactivatedCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, reason);
            result.setKeepCallback(true);
            onDeactivatedCallback.sendPluginResult(result);
        }
    }

    public void sendCommand(byte[] command) {
        Log.d(TAG, "sendCommand " + Arrays.toString(command));
        if (onCommandCallback != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, command);
            result.setKeepCallback(true);
            onCommandCallback.sendPluginResult(result);
        }
    }
}
