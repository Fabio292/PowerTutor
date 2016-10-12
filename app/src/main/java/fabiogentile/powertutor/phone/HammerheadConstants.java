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
import android.hardware.Sensor;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.HashMap;

import fabiogentile.powertutor.components.Sensors;

/*
 * Values must be in mW
 * If we want to use the values from power_profile.xml (that are expressed in mA)
 * it's necessary to multiply the value by the nominal tension (3.7V ?)
 */
public class HammerheadConstants implements PhoneConstants {
    protected static final String OPER_TMOBILE = "T - Mobile";
    protected static final String OPER_ATT = "AT&T";


    //<editor-fold desc="CPU">
    /**
     * Values expressed for 100% cpu load with all cores at the same freq
     */
    private static final double[][] cpuPowerRatiosMatrix = {
    {199.8, 288.6, 362.6, 407, 451.4, 621.6, 647.5, 691.9, 825.1, 917.6, 995.3, 1080.4, 1209.9, 1443}, //1 core
    {344.1, 418.1, 510.6, 573.5, 651.2, 847.3, 899.1, 999, 1169.2, 1443, 1594.7, 1739, 2035, 2597.4}, // 2 core
    {444, 529.1, 669.7, 747.4, 954.6, 1172.9, 1250.6, 1406, 1613.2, 1935.1, 2123.8, 2501.2, 2952.6, 3910.9}, // 3 core
    {536.5, 651.2, 917.6, 1021.2, 1191.4, 1443, 1539.2, 1757.5, 2005.4, 2560.4, 2863.8, 3189.4, 3988.6, 5302.1} // 4 core
    };

    private static final int CORE_NUMBER = 4;

    //Freqs in MHz
    private static final double[] arrayCpuFreqs = {300.0, 422.4,
            652.8, 729.6, 883.2, 960.0,
            1036.8, 1190.4, 1267.2, 1497.6,
            1574.4, 1728.0, 1958.4, 2265.6};

    /**
     * List of maps of power consumption for CPU
     */
    public static ArrayList<HashMap<Double, Double>> cpuPowerList;
    //</editor-fold>

    private static final double[] arrayGpsStatePower = {0.0, 173.55, 429.55};

    //<editor-fold desc="WIFI">
    private static final double[] arrayWifiLinkRatios = {
            47.122645, 46.354821, 43.667437, 43.283525, 40.980053, 39.44422, 38.676581,
            34.069637, 29.462693, 20.248805, 11.034917, 6.427122
    };
    private static final double[] arrayWifiLinkSpeeds = {
            1, 2, 5.5, 6, 9, 11, 12, 18, 24, 36, 48, 54
    };
    //</editor-fold>


    /* TODO: Figure out if this is really appropriate or how we should convert
     * the sensor power ratings (in mA) to mW.  I'm not sure we'll try to model
     * these thing's power usage but for the developer it's definitely interesting
     * to get some (perhaps rough?) idea of how much power sensors are using.
     */
    protected double BATTERY_VOLTAGE = 3.7;
    private double[] sensorPowerArray;

    public HammerheadConstants(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(
                Context.SENSOR_SERVICE);
        sensorPowerArray = new double[Sensors.MAX_SENSORS];

        //Collect info about sensor power consumption
        for (int i = 0; i < Sensors.MAX_SENSORS; i++) {
            Sensor sensor = sensorManager.getDefaultSensor(i);
            if (sensor != null) {
                sensorPowerArray[i] = sensor.getPower() * BATTERY_VOLTAGE;
            }
        }

        //Populate cpu power hashmap
        cpuPowerList = new ArrayList<HashMap<Double, Double>>(CORE_NUMBER);

        // Iterate through cores
        for (int i = 0; i < CORE_NUMBER; i++) {
            HashMap<Double, Double> map = new HashMap<>(arrayCpuFreqs.length);

            // Iterate throush frequencies
            int j = 0;
            for (double f: arrayCpuFreqs) {
                map.put(f, cpuPowerRatiosMatrix[i][j]);
                j++;
            }

            cpuPowerList.add(i, map);
        }


    }

    public String modelName() {
        return "hammerhead";
    }

    public double maxPower() {
        return 2300;
    }

    @Override
    public String backlightFile() {
        return "/sys/class/leds/lcd-backlight/brightness";
    }

    //<editor-fold desc="LCD">
    public double lcdBrightness() {
        //screen.full / 255
        return 2.918792157;
    }

    public double lcdBacklight() {
        //screen.on
        return 306.175;
    }
    //</editor-fold>

    //<editor-fold desc="OLED">
    public double oledBasePower() {
        throw new RuntimeException("oledBasePower() called on device with no " +
                "OLED display");
    }

    public double[] oledChannelPower() {
        throw new RuntimeException("oledChannelPower() called on device with no " +
                "OLED display");
    }

    public double oledModulation() {
        throw new RuntimeException("oledModulation() called on device with no " +
                "OLED display");
    }
    //</editor-fold>

    //<editor-fold desc="CPU">
    public ArrayList<HashMap<Double, Double>> cpuPowerRatios() {
        return cpuPowerList;
    }

    public double[] cpuFreqs() {
        return arrayCpuFreqs;
    }

    public double cpuBase() {
        return 76.22;
    }

    public int cpuCoreNumber() {
        return CORE_NUMBER;
    }
    //</editor-fold>

    public double audioPower() {
        return 384.62;
    }

    //<editor-fold desc="GPS">
    public double[] gpsStatePower() {
        return arrayGpsStatePower;
    }

    public double gpsSleepTime() {
        return 6.0;
    }
    //</editor-fold>

    //<editor-fold desc="WIFI">
    public double wifiOn() {
        return 12.95;
    }

    public double wifiLowPower() {
        return 0;
    }

    public double wifiHighPower() {
        return 271;
    }

    //PACKET PER SECONDS
    public double wifiLowHighTransition() {
        return 4;
    }

    public double wifiHighLowTransition() {
        return 4;
    }

    public double[] wifiLinkRatios() {
        return arrayWifiLinkRatios;
    }

    public double[] wifiLinkSpeeds() {
        return arrayWifiLinkSpeeds;
    }
    //</editor-fold>

    //<editor-fold desc="3G">
    public String threegInterface() {
        return "rmnet0";
    }

    public double threegIdlePower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 10;
        }
        return 10;
    }

    public double threegFachPower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 401;
        }
        return 401;
    }

    public double threegDchPower(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 570;
        }
        return 570;
    }

    public int threegDchFachDelay(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 6;
        } else if (OPER_ATT.equals(oper)) {
            return 5;
        }
        return 4;
    }

    public int threegFachIdleDelay(String oper) {
        if (OPER_TMOBILE.equals(oper)) {
            return 4;
        } else if (OPER_ATT.equals(oper)) {
            return 12;
        }
        return 6;
    }

    public int threegUplinkQueue(String oper) {
        return 151;
    }

    public int threegDownlinkQueue(String oper) {
        return 119;
    }
    //</editor-fold>

    public double[] sensorPower() {
        return sensorPowerArray;
    }

    // TODO: 11/09/16
    public double getMaxPower(String componentName) {
        if ("LCD".equals(componentName)) {
            return lcdBacklight() + lcdBrightness() * 255;
        } else if ("CPU".equals(componentName)) {
            ArrayList<HashMap<Double, Double>> ratios = cpuPowerRatios();
            return ratios.get(ratios.size() - 1).get(arrayCpuFreqs[arrayCpuFreqs.length - 1]);
        } else if ("Audio".equals(componentName)) {
            return audioPower();
        } else if ("GPS".equals(componentName)) {
            double[] gpsPow = gpsStatePower();
            return gpsPow[gpsPow.length - 1];
        } else if ("Wifi".equals(componentName)) {
            // TODO: Get a better estimation going here.
            return 800;
        } else if ("3G".equals(componentName)) {
            return threegDchPower("");
        } else if ("Sensors".equals(componentName)) {
            double res = 0;
            for (double x : sensorPower()) res += x;
            return res;
        } else {
            return 900;
        }
    }
}
