/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.nfc.dhimpl;

import android.content.Context;
import android.nfc.cardemulation.HostApduService;
import android.nfc.tech.Ndef;
import android.nfc.tech.TagTechnology;
import android.os.Bundle;
import android.util.Log;
import com.android.nfc.DeviceHost;
import com.android.nfc.NfcDiscoveryParameters;
import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

/** Native interface to the NFC Manager functions */
public class NativeNfcManager implements DeviceHost {
    private static final String TAG = "NativeNfcManager";
    static final String PREF = "NciDeviceHost";

    static final String DRIVER_NAME = "android-nci";

    static {
        System.loadLibrary("nfc_nci_jni");
    }

    /* Native structure */
    private long mNative;

    private int mIsoDepMaxTransceiveLength;
    private final DeviceHostListener mListener;
    private final Context mContext;

    private final Object mLock = new Object();
    private final HashMap<Integer, byte[]> mT3tIdentifiers = new HashMap<Integer, byte[]>();

    private static final int MIN_POLLING_FRAME_TLV_SIZE = 5;
    private static final int TAG_FIELD_CHANGE = 0;
    private static final int TAG_NFC_A = 1;
    private static final int TAG_NFC_B = 2;
    private static final int TAG_NFC_F = 3;
    private static final int TAG_NFC_UNKNOWN = 7;

    public NativeNfcManager(Context context, DeviceHostListener listener) {
        mListener = listener;
        initializeNativeStructure();
        mContext = context;
    }

    public native boolean initializeNativeStructure();

    private native boolean doDownload();

    public native int doGetLastError();

    @Override
    public boolean checkFirmware() {
        return doDownload();
    }

    private native boolean doInitialize();

    private native int getIsoDepMaxTransceiveLength();

    @Override
    public boolean initialize() {
        boolean ret = doInitialize();
        mIsoDepMaxTransceiveLength = getIsoDepMaxTransceiveLength();
        return ret;
    }

    private native void doEnableDtaMode();

    @Override
    public void enableDtaMode() {
        doEnableDtaMode();
    }

    private native void doDisableDtaMode();

    @Override
    public void disableDtaMode() {
        Log.d(TAG, "disableDtaMode : entry");
        doDisableDtaMode();
    }

    private native void doFactoryReset();

    @Override
    public void factoryReset() {
        doFactoryReset();
    }

    private native boolean doSetPowerSavingMode(boolean flag);

    @Override
    public boolean setPowerSavingMode(boolean flag) {
        return doSetPowerSavingMode(flag);
    }

    private native void doShutdown();

    @Override
    public void shutdown() {
        doShutdown();
    }

    private native boolean doDeinitialize();

    @Override
    public boolean deinitialize() {
        return doDeinitialize();
    }

    @Override
    public String getName() {
        return DRIVER_NAME;
    }

    @Override
    public native boolean sendRawFrame(byte[] data);

    @Override
    public native boolean routeAid(byte[] aid, int route, int aidInfo, int power);

    @Override
    public native boolean unrouteAid(byte[] aid);

    @Override
    public native boolean commitRouting();

    public native int doRegisterT3tIdentifier(byte[] t3tIdentifier);

    @Override
    public boolean isObserveModeSupported() {
        if (!android.nfc.Flags.nfcObserveMode()) {
            return false;
        }

        return mContext.getResources().getBoolean(
            com.android.nfc.R.bool.config_nfcObserveModeSupported);
    }

    @Override
    public native boolean setObserveMode(boolean enabled);

    @Override
    public void registerT3tIdentifier(byte[] t3tIdentifier) {
        synchronized (mLock) {
            int handle = doRegisterT3tIdentifier(t3tIdentifier);
            if (handle != 0xffff) {
                mT3tIdentifiers.put(Integer.valueOf(handle), t3tIdentifier);
            }
        }
    }

    public native void doDeregisterT3tIdentifier(int handle);

    @Override
    public void deregisterT3tIdentifier(byte[] t3tIdentifier) {
        synchronized (mLock) {
            Iterator<Integer> it = mT3tIdentifiers.keySet().iterator();
            while (it.hasNext()) {
                int handle = it.next().intValue();
                byte[] value = mT3tIdentifiers.get(handle);
                if (Arrays.equals(value, t3tIdentifier)) {
                    doDeregisterT3tIdentifier(handle);
                    mT3tIdentifiers.remove(handle);
                    break;
                }
            }
        }
    }

    @Override
    public void clearT3tIdentifiersCache() {
        synchronized (mLock) {
            mT3tIdentifiers.clear();
        }
    }

    @Override
    public native int getLfT3tMax();

    @Override
    public native void doSetScreenState(int screen_state_mask);

    @Override
    public native int getNciVersion();

    private native void doEnableDiscovery(
            int techMask,
            boolean enableLowPowerPolling,
            boolean enableReaderMode,
            boolean enableHostRouting,
            boolean enableP2p,
            boolean restart);

    @Override
    public void enableDiscovery(NfcDiscoveryParameters params, boolean restart) {
        doEnableDiscovery(
                params.getTechMask(),
                params.shouldEnableLowPowerDiscovery(),
                params.shouldEnableReaderMode(),
                params.shouldEnableHostRouting(),
                params.shouldEnableP2p(),
                restart);
    }

    @Override
    public native void disableDiscovery();

    private native void doResetTimeouts();

    @Override
    public void resetTimeouts() {
        doResetTimeouts();
    }

    @Override
    public native void doAbort(String msg);

    private native boolean doSetTimeout(int tech, int timeout);

    @Override
    public boolean setTimeout(int tech, int timeout) {
        return doSetTimeout(tech, timeout);
    }

    private native int doGetTimeout(int tech);

    @Override
    public int getTimeout(int tech) {
        return doGetTimeout(tech);
    }

    @Override
    public boolean canMakeReadOnly(int ndefType) {
        return (ndefType == Ndef.TYPE_1 || ndefType == Ndef.TYPE_2);
    }

    @Override
    public int getMaxTransceiveLength(int technology) {
        switch (technology) {
            case (TagTechnology.NFC_A):
            case (TagTechnology.MIFARE_CLASSIC):
            case (TagTechnology.MIFARE_ULTRALIGHT):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.NFC_B):
                /////////////////////////////////////////////////////////////////
                // Broadcom: Since BCM2079x supports this, set NfcB max size.
                // return 0; // PN544 does not support transceive of raw NfcB
                return 253; // PN544 does not support transceive of raw NfcB
            case (TagTechnology.NFC_V):
                return 253; // PN544 RF buffer = 255 bytes, subtract two for CRC
            case (TagTechnology.ISO_DEP):
                return mIsoDepMaxTransceiveLength;
            case (TagTechnology.NFC_F):
                return 255;
            default:
                return 0;
        }
    }

    public native int getAidTableSize();

    private native void doSetP2pInitiatorModes(int modes);

    @Override
    public void setP2pInitiatorModes(int modes) {
        doSetP2pInitiatorModes(modes);
    }

    private native void doSetP2pTargetModes(int modes);

    @Override
    public void setP2pTargetModes(int modes) {
        doSetP2pTargetModes(modes);
    }

    @Override
    public boolean getExtendedLengthApdusSupported() {
        /* 261 is the default size if extended length frames aren't supported */
        if (getMaxTransceiveLength(TagTechnology.ISO_DEP) > 261) return true;
        return false;
    }

    private native void doDump(FileDescriptor fd);

    @Override
    public void dump(FileDescriptor fd) {
        doDump(fd);
    }

    private native void doEnableScreenOffSuspend();

    @Override
    public boolean enableScreenOffSuspend() {
        doEnableScreenOffSuspend();
        return true;
    }

    private native void doDisableScreenOffSuspend();

    @Override
    public boolean disableScreenOffSuspend() {
        doDisableScreenOffSuspend();
        return true;
    }

    private native boolean doSetNfcSecure(boolean enable);

    @Override
    public boolean setNfcSecure(boolean enable) {
        return doSetNfcSecure(enable);
    }

    @Override
    public native String getNfaStorageDir();

    private native void doStartStopPolling(boolean start);

    @Override
    public void startStopPolling(boolean start) {
        doStartStopPolling(start);
    }

    private native void doSetNfceePowerAndLinkCtrl(boolean enable);

    @Override
    public void setNfceePowerAndLinkCtrl(boolean enable) {
        doSetNfceePowerAndLinkCtrl(enable);
    }

    @Override
    public native byte[] getRoutingTable();

    @Override
    public native int getMaxRoutingTableSize();

    /** Notifies Ndef Message (TODO: rename into notifyTargetDiscovered) */
    private void notifyNdefMessageListeners(NativeNfcTag tag) {
        mListener.onRemoteEndpointDiscovered(tag);
    }

    private void notifyHostEmuActivated(int technology) {
        mListener.onHostCardEmulationActivated(technology);
    }

    private void notifyHostEmuData(int technology, byte[] data) {
        mListener.onHostCardEmulationData(technology, data);
    }

    private void notifyHostEmuDeactivated(int technology) {
        mListener.onHostCardEmulationDeactivated(technology);
    }

    private void notifyRfFieldActivated() {
        mListener.onRemoteFieldActivated();
    }

    private void notifyRfFieldDeactivated() {
        mListener.onRemoteFieldDeactivated();
    }

    private void notifyTransactionListeners(byte[] aid, byte[] data, String evtSrc) {
        mListener.onNfcTransactionEvent(aid, data, evtSrc);
    }

    private void notifyEeUpdated() {
        mListener.onEeUpdated();
    }

    private void notifyHwErrorReported() {
        mListener.onHwErrorReported();
    }

    private void notifyPollingLoopFrame(int data_len, byte[] p_data) {
        if (data_len < MIN_POLLING_FRAME_TLV_SIZE) {
            return;
        }
        Bundle frame = new Bundle();
        final int header_len = 2;
        int pos = header_len;
        final int TLV_len_offset = 0;
        final int TLV_type_offset = 2;
        final int TLV_timestamp_offset = 3;
        final int TLV_gain_offset = 7;
        final int TLV_data_offset = 8;
        while (pos + TLV_len_offset < data_len) {
        int type = p_data[pos + TLV_type_offset];
        int length = p_data[pos + TLV_len_offset];
        if (pos + length + 1 > data_len) {
            // Frame is bigger than buffer.
            Log.e(TAG, "Polling frame data is longer than buffer data length.");
            break;
        }
        switch (type) {
            case TAG_FIELD_CHANGE:
                frame.putChar(
                    HostApduService.POLLING_LOOP_TYPE_KEY,
                    p_data[pos + TLV_data_offset] != 0x00
                        ? HostApduService.POLLING_LOOP_TYPE_ON
                        : HostApduService.POLLING_LOOP_TYPE_OFF);
                break;
            case TAG_NFC_A:
                frame.putChar(HostApduService.POLLING_LOOP_TYPE_KEY,
                    HostApduService.POLLING_LOOP_TYPE_A);
                break;
            case TAG_NFC_B:
                frame.putChar(HostApduService.POLLING_LOOP_TYPE_KEY,
                    HostApduService.POLLING_LOOP_TYPE_B);
                break;
            case TAG_NFC_F:
                frame.putChar(HostApduService.POLLING_LOOP_TYPE_KEY,
                    HostApduService.POLLING_LOOP_TYPE_F);
                break;
            case TAG_NFC_UNKNOWN:
                frame.putChar(
                    HostApduService.POLLING_LOOP_TYPE_KEY,
                    HostApduService.POLLING_LOOP_TYPE_UNKNOWN);
                frame.putByteArray(
                    HostApduService.POLLING_LOOP_DATA_KEY,
                    Arrays.copyOfRange(
                        p_data, pos + TLV_data_offset, pos + TLV_timestamp_offset + length));
                break;
            default:
                Log.e(TAG, "Unknown polling loop tag type.");
        }
        if (pos + TLV_gain_offset <= data_len) {
            byte gain = p_data[pos + TLV_gain_offset];
            frame.putByte(HostApduService.POLLING_LOOP_GAIN_KEY, gain);
        }
        if (pos + TLV_timestamp_offset + 3 < data_len) {
            int timestamp = ByteBuffer.wrap(p_data, pos + TLV_timestamp_offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            frame.putInt(HostApduService.POLLING_LOOP_TIMESTAMP_KEY, timestamp);
        }
        pos += (length + 2);
        }
        mListener.onPollingLoopDetected(frame);
    }
}