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

package fabiogentile.powertutor.phone;

import android.content.Context;
import android.util.Log;

import fabiogentile.powertutor.components.Audio.AudioData;
import fabiogentile.powertutor.components.CPU.CpuData;
import fabiogentile.powertutor.components.GPS;
import fabiogentile.powertutor.components.GPS.GpsData;
import fabiogentile.powertutor.components.LCD.LcdData;
import fabiogentile.powertutor.components.OLED.OledData;
import fabiogentile.powertutor.components.Sensors;
import fabiogentile.powertutor.components.Sensors.SensorData;
import fabiogentile.powertutor.components.Threeg;
import fabiogentile.powertutor.components.Threeg.ThreegData;
import fabiogentile.powertutor.components.Wifi;
import fabiogentile.powertutor.components.Wifi.WifiData;

public class HammerheadPowerCalculator implements PhonePowerCalculator {

    private static final String TAG = "HammerheadPC";
    protected PhoneConstants coeffs;
    private double[] powerRatios;
    private double[] freqs;

    public HammerheadPowerCalculator(Context context) {
        this(new HammerheadConstants(context));
    }

    protected HammerheadPowerCalculator(PhoneConstants coeffs) {
        this.coeffs = coeffs;
        this.powerRatios = coeffs.cpuPowerRatios();
        this.freqs = coeffs.cpuFreqs();
    }

    /* Returns the largest index y such that if x were inserted into A (which
     * should already be sorted) at y then A would remain sorted.
     */
    protected static int upperBound(double[] A, double x) {
        int lo = 0;
        int hi = A.length;
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (A[mid] <= x) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public static double getTESTPower(CpuData data) {
        final double[] arrayCpuPowerRatios = {214.23, 326.34,
                368.52, 513.56, 553.52, 629.74,
                659.71, 699.67, 858.77, 949.05,
                985.68, 1064.49, 1205.09, 1428.94};
        //Freqs in MHz
        final double[] arrayCpuFreqs = {300.0, 422.4,
                652.8, 729.6, 883.2, 960.0,
                1036.8, 1190.4, 1267.2, 1497.6,
                1574.4, 1728.0, 1958.4, 2265.6};
        double ratio = arrayCpuPowerRatios[0];
        double ret = 0;

        for (int i = 0; i < arrayCpuFreqs.length; i++) {
            if (arrayCpuFreqs[i] == data.freq) {
                ratio = arrayCpuPowerRatios[i];
                break;
            }
        }

        ret = Math.max(0, ratio * (data.usrPerc + data.sysPerc));

        return ret;
    }

    public double getLcdPower(LcdData data) {
        double ret = data.screenOn ?
                coeffs.lcdBrightness() * data.brightness + coeffs.lcdBacklight() : 0;

        //Log.i(TAG, "getLcdPower: " + ret);

        return ret;
    }

    public double getOledPower(OledData data) {
        throw new RuntimeException("getOledPower() should not be called for Hammerhead");
    }

    public double getCpuPower(CpuData data) {
        /* Find the two nearest cpu frequency and linearly interpolate
         * the power ratio for that frequency.
         */
        double ratio = powerRatios[0];
        double ret = 0;
        boolean found = false;

        // TODO: 16/08/16 HashMap?
        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] == data.freq) {
                ratio = powerRatios[i];
                found = true;
                break;
            }
        }

        if (!found) {
            Log.e(TAG, "getCpuPower: FREQ not found: " + data.freq);
            ratio = powerRatios[0];
        }

        ret = Math.max(0, ratio * (data.usrPerc + data.sysPerc));

        if (data.isUidAll) {
            ret += coeffs.cpuBase(); //Add base cpu power for uid_ALL
            //Log.i(TAG, "getCpuPower: " + ret);
        }

        return ret;
    }

    public double getAudioPower(AudioData data) {
        return data.musicOn ? coeffs.audioPower() : 0;
    }

    public double getGpsPower(GpsData data) {
        double result = 0;
        double statePower[] = coeffs.gpsStatePower();
        for (int i = 0; i < GPS.POWER_STATES; i++) {
            result += data.stateTimes[i] * statePower[i];
        }
        return result;
    }

    public double getWifiPower(WifiData data) {
        if (!data.wifiOn) {
            return 0;
        } else if (data.powerState == Wifi.POWER_STATE_LOW) {
            return coeffs.wifiLowPower() * data.uploadPercent;
        } else if (data.powerState == Wifi.POWER_STATE_HIGH) {
            double[] linkSpeeds = coeffs.wifiLinkSpeeds();
            double[] linkRatios = coeffs.wifiLinkRatios();
            double ratio;
            if (linkSpeeds.length == 1) {
                /* If there is only one set speed we have to use its ratio as we have
                 * nothing else to go on.
                 */
                ratio = linkRatios[0];
            } else {
                /* Find the two nearest speed/ratio pairs and linearly interpolate
                 * the ratio for this link speed.
                 */
                int ind = upperBound(linkSpeeds, data.linkSpeed);
                if (ind == 0) ind++;
                if (ind == linkSpeeds.length) ind--;
                ratio = linkRatios[ind - 1] + (linkRatios[ind] - linkRatios[ind - 1]) /
                        (linkSpeeds[ind] - linkSpeeds[ind - 1]) *
                        (data.linkSpeed - linkSpeeds[ind - 1]);
            }
            double ret = Math.max(0, coeffs.wifiHighPower() + ratio * data.uplinkRate);

            // Scale energy consumption basing on the percentage of data transmitted
            ret *= data.uploadPercent;

            return ret;
        }
        throw new RuntimeException("Unexpected power state");
    }

    public double getThreeGPower(ThreegData data) {
        if (!data.threegOn) {
            return 0;
        } else {
            switch (data.powerState) {
                case Threeg.POWER_STATE_IDLE:
                    return coeffs.threegIdlePower(data.oper);
                case Threeg.POWER_STATE_FACH:
                    return coeffs.threegFachPower(data.oper);
                case Threeg.POWER_STATE_DCH:
                    return coeffs.threegDchPower(data.oper);
            }
        }
        return 0;
    }

    public double getSensorPower(SensorData data) {
        double result = 0;
        double[] powerUse = coeffs.sensorPower();
        for (int i = 0; i < Sensors.MAX_SENSORS; i++) {
            result += data.onTime[i] * powerUse[i];
        }
        return result;
    }
}

