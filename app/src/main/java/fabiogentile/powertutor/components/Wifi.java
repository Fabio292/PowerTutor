/*
Copyright (C) 2011 The University of Michigan

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Please send inquiries to powertutor@umich.edu
*/

package fabiogentile.powertutor.components;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;

import fabiogentile.powertutor.phone.PhoneConstants;
import fabiogentile.powertutor.service.IterationData;
import fabiogentile.powertutor.service.PowerData;
import fabiogentile.powertutor.util.Recycler;
import fabiogentile.powertutor.util.SystemInfo;

public class Wifi extends PowerComponent {
    public static final int POWER_STATE_LOW = 0;
    public static final int POWER_STATE_HIGH = 1;
    public static final int BYTE_PER_PACKET_OVERHEAD = 52;
    public static final String[] POWER_STATE_NAMES = {"LOW", "HIGH"};
    private static final String TAG = "Wifi";
    private final String DEFUAULT_INTERFACE_NAME = "wlan0";
    private PhoneConstants phoneConstants;
    private WifiManager wifiManager;
    private SystemInfo sysInfo;
    private long lastLinkSpeed;
    private int[] lastUids;
    private WifiStateKeeper wifiStateAll;
    private SparseArray<WifiStateKeeper> uidStates;
    private String transPacketsFile;
    private String readPacketsFile;
    private String transBytesFile;
    private String readBytesFile;
    private long prevTxBytes = 0;
    private long prevRxBytes = 0;
    private long prevTxPkt = 0;
    private long prevRxPkt = 0;
    private File uidStatsFolder;
    private String BASE_WIFI_DIRECTORY = "/sys/class/net/"; // TODO: 11/08/16 spostare nella classe specifica del device e recuperare da constants


    public Wifi(Context context, PhoneConstants phoneConstants) {
        this.phoneConstants = phoneConstants;
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        sysInfo = SystemInfo.getInstance();

        /* Try to grab the interface name.  If we can't find it will take a wild
         * stab in the dark.
         */
        String interfaceName = SystemInfo.getInstance().getProperty("wifi.interface");
        if (interfaceName == null)
            interfaceName = DEFUAULT_INTERFACE_NAME;

        lastLinkSpeed = -1;
        wifiStateAll = new WifiStateKeeper(phoneConstants.wifiHighLowTransition(),
                phoneConstants.wifiLowHighTransition());
        uidStates = new SparseArray<WifiStateKeeper>();

        //Stats file
        transPacketsFile = BASE_WIFI_DIRECTORY + interfaceName + "/statistics/tx_packets";
        readPacketsFile = BASE_WIFI_DIRECTORY + interfaceName + "/statistics/rx_packets";
        transBytesFile = BASE_WIFI_DIRECTORY + interfaceName + "/statistics/tx_bytes";
        readBytesFile = BASE_WIFI_DIRECTORY + interfaceName + "/statistics/rx_bytes";

        uidStatsFolder = new File("/proc/uid_stat");
    }

    @Override
    public IterationData calculateIteration(long iteration) {
        IterationData result = IterationData.obtain();


        int wifiStateFlag = wifiManager.getWifiState();
        //Manage Wifi OFF
        if (wifiStateFlag != WifiManager.WIFI_STATE_ENABLED &&
                wifiStateFlag != WifiManager.WIFI_STATE_DISABLING) {

            /* We need to allow the real iterface state keeper to reset it's state
             * so that the next update it knows it's coming back from an off state.
             * We also need to clear all the uid information.
             */
            wifiStateAll.interfaceOff();
            uidStates.clear();
            lastLinkSpeed = -1;

            WifiData data = WifiData.obtain();
            data.init();
            result.setPowerData(data);
            return result;
        }

        long totTransmitPackets = sysInfo.readLongFromFile(transPacketsFile);
        long totReceivePackets = sysInfo.readLongFromFile(readPacketsFile);
        long totTransmitBytes = sysInfo.readLongFromFile(transBytesFile);
        long totReceiveBytes = sysInfo.readLongFromFile(readBytesFile);

        long deltaTxPkt = totTransmitPackets - this.prevTxPkt;
        this.prevTxPkt = totTransmitPackets;

        long deltaRxPkt = totReceivePackets - this.prevRxPkt;
        this.prevRxPkt = totReceivePackets;

        /**
         * Correct the value of byte sent and received since the total value
         * include also the overhead (52 byte per packet in TPC)
         */
        long deltaTxBytes = totTransmitBytes - this.prevTxBytes
                - (deltaTxPkt * BYTE_PER_PACKET_OVERHEAD);
        if(deltaTxBytes < 0)
            deltaTxBytes = 0;
        this.prevTxBytes = totTransmitBytes;

        long deltaRxBytes = totReceiveBytes - this.prevRxBytes
                - (deltaRxPkt * BYTE_PER_PACKET_OVERHEAD);
        if (deltaRxBytes < 0)
            deltaRxBytes = 0;
        this.prevRxBytes = totReceiveBytes;

        Log.d(TAG, "calculateIteration: deltaRX: " + deltaRxBytes + "(" +
                deltaRxPkt + ") deltaTX: " + deltaTxBytes + "(" + deltaTxPkt + ")");

        if (totTransmitPackets == -1 || totReceivePackets == -1 || totTransmitBytes == -1 || totReceiveBytes == -1) {
            /* Couldn't read interface data files. */
            Log.e(TAG, "Failed to read packet and byte counts from wifi interface");
            return result;
        }

        /* Update the link speed every 30 seconds as pulling the WifiInfo structure
         * from WifiManager is a little bit expensive.  This isn't really something
         * that is likely to change very frequently anyway.
         */
        if (iteration % 30 == 0 || lastLinkSpeed == -1) {
            lastLinkSpeed = wifiManager.getConnectionInfo().getLinkSpeed();
        }
        double linkSpeed = lastLinkSpeed;

        if (wifiStateAll.isInitialized()) {
            wifiStateAll.updateState(totTransmitPackets, totReceivePackets, totTransmitBytes, totReceiveBytes);

            WifiData data = WifiData.obtain();
            data.init(wifiStateAll.getPackets(), wifiStateAll.getUplinkBytes(),
                    wifiStateAll.getDownlinkBytes(), wifiStateAll.getUplinkRate(),
                    linkSpeed, wifiStateAll.getPowerState(), 1.0, 1.0);

            result.setPowerData(data);
        } else {
            // Do initialization
            wifiStateAll.updateState(totTransmitPackets, totReceivePackets,
                    totTransmitBytes, totReceiveBytes);
        }

        lastUids = sysInfo.getUids(lastUids);
        if (lastUids != null) {
            //Loop through each uid
            // TODO: 06/10/16 Aggiungere un loop dove si conta la percentuale per ogni UID senza basarsi sui valori delta
            for (int uid : lastUids) {
                if (uid == -1) {
                    continue;
                }
                try {
                    WifiStateKeeper uidState = uidStates.get(uid);
                    if (uidState == null) {
                        // New UID
                        uidState = new WifiStateKeeper(phoneConstants.wifiHighLowTransition(),
                                phoneConstants.wifiLowHighTransition());
                        uidStates.put(uid, uidState);
                    }

                    if (!uidState.isStale()) {
                        /* We use a heuristic here so that we don't poll for uids that haven't
                         * had much activity recently.
                         */
                        continue;
                    }

                    // These read operations are the expensive part of polling.
                    long receiveBytes = sysInfo.readLongFromFile("/proc/uid_stat/" + uid + "/tcp_rcv");
                    long transmitBytes = sysInfo.readLongFromFile("/proc/uid_stat/" + uid + "/tcp_snd");


                    if (receiveBytes == -1 || transmitBytes == -1) {
                        Log.w(TAG, "Failed to read uid read/write byte counts for UID: " + uid);
                    } else if (uidState.isInitialized()) {
                        /* Calculate the estimate number of packet exchanged by dividing
                         * the exchange nubmer of bytes per the average packet size
                         */
                        long deltaTransmitBytes = transmitBytes - uidState.getTransmitBytes();
                        long deltaReceiveBytes = receiveBytes - uidState.getReceiveBytes();
                        long estimatedTransmitPackets = Math.round(deltaTransmitBytes /
                                wifiStateAll.getAverageTransmitPacketSize());
                        long estimatedReceivePackets = Math.round(deltaReceiveBytes /
                                wifiStateAll.getAverageReceivePacketSize());

                        if (deltaTransmitBytes > 0 && estimatedTransmitPackets == 0) {
                            estimatedTransmitPackets = 1;
                        }
                        if (deltaReceiveBytes > 0 && estimatedReceivePackets == 0) {
                            estimatedReceivePackets = 1;
                        }

                        boolean active = transmitBytes != uidState.getTransmitBytes() ||
                                receiveBytes != uidState.getReceiveBytes();
                        uidState.updateState(
                                uidState.getTransmitPackets() + estimatedTransmitPackets,
                                uidState.getReceivePackets() + estimatedReceivePackets,
                                transmitBytes, receiveBytes);

                        if (active) {
                            WifiData uidData = WifiData.obtain();
                            double upPerc, downPerc;
                            // TODO: 06/10/16 se i delta sono 0 ma ho dei valori in ritardo?
                            if(deltaRxBytes == 0 || deltaTxBytes == 0){
                                upPerc = 0;
                                downPerc = 0;
                            }
                            else {
                                upPerc = ((double) deltaTransmitBytes / (double) deltaTxBytes);
                                downPerc = ((double) deltaReceiveBytes / (double) deltaRxBytes);
                            }

                            uidData.init(uidState.getPackets(), uidState.getUplinkBytes(),
                                    uidState.getDownlinkBytes(), uidState.getUplinkRate(),
                                    linkSpeed, uidState.getPowerState(),
                                    upPerc, downPerc);
                            result.addUidPowerData(uid, uidData);

                            if ((deltaReceiveBytes + deltaTransmitBytes) > 0)
                                Log.d(TAG, "calculateIteration: UID: " + uid + " RX: " + deltaReceiveBytes +
                                        "(" + downPerc + ") TX: " + deltaTransmitBytes +
                                        "(" + upPerc + ")");
                        }

                    } else {
                        //First time we encounter this UID
                        uidState.updateState(0, 0, transmitBytes, receiveBytes);
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Non-uid files in /proc/uid_stat!");
                }
            }
        }
        Log.d(TAG, "calculateIteration: ");
        return result;
    }

    private long readLongFromFile(String filePath) {
        return sysInfo.readLongFromFile(filePath);
    }

    @Override
    public boolean hasUidInformation() {
        return uidStatsFolder.exists();
    }

    @Override
    public String getComponentName() {
        return "Wifi";
    }

    public static class WifiData extends PowerData {
        private static Recycler<WifiData> recycler = new Recycler<WifiData>();
        public boolean wifiOn;
        public double packets;
        public long uplinkBytes;
        public long downlinkBytes;
        public double uplinkRate;
        public double linkSpeed;
        public int powerState;

        /**
         * Percentage of uploaded data wrt the total
         */
        public double uploadPercent;
        /**
         * Percentage of downloaded data wrt the total
         */
        public double downloadPercent;

        private WifiData() {
        }

        public static WifiData obtain() {
            WifiData result = recycler.obtain();
            if (result != null) return result;
            return new WifiData();
        }

        @Override
        public void recycle() {
            recycler.recycle(this);
        }

        public void init(double packets, long uplinkBytes, long downlinkBytes,
                         double uplinkRate, double linkSpeed, int powerState,
                         double upPerc, double downPerc) {
            wifiOn = true;
            this.packets = packets;
            this.uplinkBytes = uplinkBytes;
            this.downlinkBytes = downlinkBytes;
            this.uplinkRate = uplinkRate;
            this.linkSpeed = linkSpeed;
            this.powerState = powerState;

            this.uploadPercent = upPerc;
            this.downloadPercent = downPerc;
        }

        public void init() {
            wifiOn = false;
        }

        public void writeLogDataInfo(OutputStreamWriter out) throws IOException {
            StringBuilder res = new StringBuilder();
            res.append("Wifi+on+").append(wifiOn).append("\n");
            if (wifiOn) {
                res.append("Wifi+packets+").append(Math.round(packets))
                        .append("\nWifi+uplinkBytes+").append(uplinkBytes)
                        .append("\nWifi+downlinkBytes+").append(downlinkBytes)
                        .append("\nWifi+uplink+").append(Math.round(uplinkRate))
                        .append("\nWifi+speed+").append(Math.round(linkSpeed))
                        .append("\nWifi+state+").append(Wifi.POWER_STATE_NAMES[powerState])
                        .append("\n");
            }
            out.write(res.toString());
        }
    }

    private static class WifiStateKeeper {
        private long lastTransmitPackets;
        private long lastReceivePackets;
        private long lastTransmitBytes;
        private long lastReceiveBytes;
        private long lastTime;

        private int powerState;
        private double lastPackets;
        private double lastUplinkRate;
        private double lastAverageTransmitPacketSize;
        private double lastAverageReceivePacketSize;

        private long deltaUplinkBytes;
        private long deltaDownlinkBytes;

        private double highLowTransition;
        private double lowHighTransition;

        private long inactiveTime;

        public WifiStateKeeper(double highLowTransition, double lowHighTransition) {
            this.highLowTransition = highLowTransition;
            this.lowHighTransition = lowHighTransition;
            lastTransmitPackets = lastReceivePackets = lastTransmitBytes = lastTime = -1;

            powerState = POWER_STATE_LOW;
            lastPackets = lastUplinkRate = 0;
            lastAverageTransmitPacketSize = 1000;
            lastAverageReceivePacketSize = 1000;
            inactiveTime = 0;
        }

        public void interfaceOff() {
            lastTime = SystemClock.elapsedRealtime();
            powerState = POWER_STATE_LOW;
        }

        public boolean isInitialized() {
            return lastTime != -1;
        }

        /**
         * Update information and search for a state change
         */
        public void updateState(long transmitPackets, long receivePackets,
                                long transmitBytes, long receiveBytes) {
            long curTime = SystemClock.elapsedRealtime();

            if (lastTime != -1 && curTime > lastTime) {
                double deltaTime = curTime - lastTime;

                // TODO: 14/09/16 MAGIC?? HACK?? 7.8125??
                lastUplinkRate = (transmitBytes - lastTransmitBytes) / 1024.0 * 7.8125 / deltaTime;
                //Log.d(TAG, "updateState: delta byte = " + (transmitBytes - lastTransmitBytes) +
                //         " lastUplinkRate = " + lastUplinkRate);

                lastPackets = receivePackets + transmitPackets - lastReceivePackets - lastTransmitPackets;
                deltaUplinkBytes = transmitBytes - lastTransmitBytes;
                deltaDownlinkBytes = receiveBytes - lastReceiveBytes;

                //<editor-fold desc="Avg pkt size">
                if (transmitPackets != lastTransmitPackets) {
                    // Calculate the average number of bytes in packet sent
                    lastAverageTransmitPacketSize = 0.9 * lastAverageTransmitPacketSize +
                            0.1 * (transmitBytes - lastTransmitBytes) / (transmitPackets - lastTransmitPackets);
                }

                if (receivePackets != lastReceivePackets) {
                    // Calculate the average number of bytes in packet received
                    lastAverageReceivePacketSize = 0.9 * lastAverageReceivePacketSize +
                            0.1 * (receiveBytes - lastReceiveBytes) / (receivePackets - lastReceivePackets);
                }
                //</editor-fold>

                // Update inactivity timer
                if (receiveBytes != lastReceiveBytes || transmitBytes != lastTransmitBytes) {
                    inactiveTime = 0;
                } else {
                    inactiveTime += curTime - lastTime;
                }

                // Update power state according to transition level
                if (lastPackets < highLowTransition) {
                    powerState = POWER_STATE_LOW;
                } else if (lastPackets > lowHighTransition) {
                    powerState = POWER_STATE_HIGH;
                }
            }

            lastTime = curTime;
            lastTransmitPackets = transmitPackets;
            lastReceivePackets = receivePackets;
            lastTransmitBytes = transmitBytes;
            lastReceiveBytes = receiveBytes;
        }

        public int getPowerState() {
            return powerState;
        }

        public double getPackets() {
            return lastPackets;
        }

        public long getUplinkBytes() {
            return deltaUplinkBytes;
        }

        public long getDownlinkBytes() {
            return deltaDownlinkBytes;
        }

        public double getUplinkRate() {
            return lastUplinkRate;
        }

        public double getAverageTransmitPacketSize() {
            return lastAverageTransmitPacketSize;
        }

        public double getAverageReceivePacketSize() {
            return lastAverageReceivePacketSize;
        }

        public long getTransmitPackets() {
            return lastTransmitPackets;
        }

        public long getReceivePackets() {
            return lastReceivePackets;
        }

        public long getTransmitBytes() {
            return lastTransmitBytes;
        }

        public long getReceiveBytes() {
            return lastReceiveBytes;
        }

        /* The idea here is that we don't want to have to read uid information
         * every single iteration for each uid as it just takes too long.  So here
         * we are designing a hueristic that helps us avoid polling for too many
         * uids.
         */
        public boolean isStale() {
            long curTime = SystemClock.elapsedRealtime();
            return curTime - lastTime > Math.min(10000, inactiveTime);
        }
    }
}
