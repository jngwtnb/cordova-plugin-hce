// Cordova HCE Plugin
// (c) 2015 Don Coleman

package com.megster.cordova.hce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class CordovaApduService extends HostApduService {

    private static final String TAG = "CordovaApduService";

    // tight binding between the service and plugin
    // future versions could use bind
    private static HCEPlugin hcePlugin;
    private static CordovaApduService cordovaApduService;

    private final static byte[] SELECT_APP = new byte[] {(byte)0x00, (byte)0xa4, (byte)0x04, (byte)0x00,
        (byte)0x07, (byte)0xd2, (byte)0x76, (byte)0x00, (byte)0x00, (byte)0x85, (byte)0x01, (byte)0x01,
        (byte)0x00,
    };

    private final static byte[] SELECT_CC_FILE = new byte[] {(byte)0x00, (byte)0xa4, (byte)0x00, (byte)0x0c,
            (byte)0x02, (byte)0xe1, (byte)0x03,
    };

    private final static byte[] SELECT_NDEF_FILE = new byte[] {(byte)0x00, (byte)0xa4, (byte)0x00, (byte)0x0c,
            (byte)0x02, (byte)0xe1, (byte)0x04,
    };

    private final static byte[] SUCCESS_SW = new byte[] {
            (byte)0x90, (byte)0x00,
    };

    private final static byte[] CC_FILE = new byte[] {
            0x00, 0x0f, // CCLEN
            0x20, // Mapping Version
            0x00, 0x3b, // Maximum R-APDU data size
            0x00, 0x34, // Maximum C-APDU data size
            0x04, 0x06, // Tag & Length
            (byte)0xe1, 0x04, // NDEF File Identifier
            0x01, 0x00, // Maximum NDEF size
            0x00, // NDEF file read access granted
            (byte)0xff, // NDEF File write access denied
    };

    private byte[] ndefRecordFile;
    private boolean appSelected;
    private boolean ccSelected;
    private boolean ndefSelected;

    static byte[] ndefMessage;

    static void setHCEPlugin(HCEPlugin _hcePlugin) {
        hcePlugin = _hcePlugin;
    }

    static boolean sendResponse(byte[] data) {
        if (cordovaApduService != null) {
            cordovaApduService.sendResponseApdu(data);
            return true;
        } else {
            return false;
        }
    }

    /**
     * This method will be called when a command APDU has been received from a remote device. A
     * response APDU can be provided directly by returning a byte-array in this method. In general
     * response APDUs must be sent as quickly as possible, given the fact that the user is likely
     * holding his device over an NFC reader when this method is called.
     *
     * <p class="note">If there are multiple services that have registered for the same AIDs in
     * their meta-data entry, you will only get called if the user has explicitly selected your
     * service, either as a default or just for the next tap.
     *
     * <p class="note">This method is running on the main thread of your application. If you
     * cannot return a response APDU immediately, return null and use the {@link
     * #sendResponseApdu(byte[])} method later.
     *
     * @param commandApdu The APDU that received from the remote device
     * @param extras A bundle containing extra data. May be null.
     * @return a byte-array containing the response APDU, or null if no response APDU can be sent
     * at this point.
     */

    @Override
    public void onCreate() {
        super.onCreate();

        appSelected = false;
        ccSelected = false;
        ndefSelected = false;
        ndefRecordFile = null;
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        Log.i(TAG, "Received APDU: " + ByteArrayToHexString(commandApdu));

        // save a reference in static variable (hack)
        cordovaApduService = this;

        if (ndefRecordFile == null && ndefMessage != null) {
            int nlen = ndefMessage.length;

            ndefRecordFile = new byte[nlen + 2];

            ndefRecordFile[0] = (byte)((nlen & 0xff00) / 256);
            ndefRecordFile[1] = (byte)(nlen & 0xff);
            System.arraycopy(ndefMessage, 0, ndefRecordFile, 2, ndefMessage.length);
        }

        if (Arrays.equals(SELECT_APP, commandApdu)) {
            appSelected = true;
            ccSelected = false;
            ndefSelected = false;
            return SUCCESS_SW;
        } else if (appSelected && Arrays.equals(SELECT_CC_FILE, commandApdu)) {
            ccSelected = true;
            ndefSelected = false;
            return SUCCESS_SW;
        } else if (appSelected && Arrays.equals(SELECT_NDEF_FILE, commandApdu)) {
            ccSelected = false;
            ndefSelected = true;
            return SUCCESS_SW;
        } else if (commandApdu[0] == (byte)0x00 && commandApdu[1] == (byte)0xb0) {
            int offset = (0x00ff & commandApdu[2]) * 256 + (0x00ff & commandApdu[3]);
            int le = 0x00ff & commandApdu[4];

            byte[] responseApdu = new byte[le + SUCCESS_SW.length];

            if (ccSelected && offset == 0 && le == CC_FILE.length) {
                System.arraycopy(CC_FILE, offset, responseApdu, 0, le);
                System.arraycopy(SUCCESS_SW, 0, responseApdu, le, SUCCESS_SW.length);

                return responseApdu;
            } else if (ndefSelected) {
                if (offset + le <= ndefRecordFile.length) {
                    System.arraycopy(ndefRecordFile, offset, responseApdu, 0, le);
                    System.arraycopy(SUCCESS_SW, 0, responseApdu, le, SUCCESS_SW.length);

                    return responseApdu;
                }
            }
        }

        if (hcePlugin != null) {
            hcePlugin.sendCommand(commandApdu);
        } else {
            Log.e(TAG, "No reference to HCE Plugin.");
        }

        // return null since JavaScript code will send the response
        return null;
    }

    /**
     * Called if the connection to the NFC card is lost, in order to let the application know the
     * cause for the disconnection (either a lost link, or another AID being selected by the
     * reader).
     *
     * @param reason Either DEACTIVATION_LINK_LOSS or DEACTIVATION_DESELECTED
     */
    @Override
    public void onDeactivated(int reason) {
        appSelected = false;
        ccSelected = false;
        ndefSelected = false;

        ndefRecordFile = null;

        if (hcePlugin != null) {
            hcePlugin.deactivated(reason);
        } else {
            Log.e(TAG, "No reference to HCE Plugin.");
        }

    }

    /**
     * Utility method to convert a byte array to a hexadecimal string.
     *
     * @param bytes Bytes to convert
     * @return String, containing hexadecimal representation.
     */
    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2]; // Each byte has two hex characters (nibbles)
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF; // Cast bytes[j] to int, treating as unsigned value
            hexChars[j * 2] = hexArray[v >>> 4]; // Select hex character from upper nibble
            hexChars[j * 2 + 1] = hexArray[v & 0x0F]; // Select hex character from lower nibble
        }
        return new String(hexChars);
    }

}
