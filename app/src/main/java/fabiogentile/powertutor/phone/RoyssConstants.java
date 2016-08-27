package fabiogentile.powertutor.phone;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

import fabiogentile.powertutor.components.Sensors;

public class RoyssConstants extends HammerheadConstants {
    private static final double[] arrayCpuPowerRatios = {55.0, 148.0, 249.0, 408.0, 577.0};
    //Freqs in MHz
    private static final double[] arrayCpuFreqs = {245.76, 320.0, 480.0, 600.0, 1008.0};

    private static final double[] arrayGpsStatePower = {0.0, 173.55, 429.55};

    private static final double cpuBaseCond = 0.0; // TODO: 25/08/16 mettere valore

    private static final double[] arrayWifiLinkRatios = {
            47.122645, 46.354821, 43.667437, 43.283525, 40.980053, 39.44422, 38.676581,
            34.069637, 29.462693, 20.248805, 11.034917, 6.427122
    };
    private static final double[] arrayWifiLinkSpeeds = {
            1, 2, 5.5, 6, 9, 11, 12, 18, 24, 36, 48, 54
    };

    /* TODO: Figure out if this is really appropriate or how we should convert
     * the sensor power ratings (in mA) to mW.  I'm not sure we'll try to model
     * these thing's power usage but for the developer it's definitely interesting
     * to get some (perhaps rough?) idea of how much power sensors are using.
     */
    protected double BATTERY_VOLTAGE = 3.7;
    private double[] sensorPowerArray;

    public RoyssConstants(Context context) {
        super(context);

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
    }

    public String modelName() {
        return "royss";
    }

    public double maxPower() {
        return 2100;
    }

    @Override
    public String backlightFile() {
        return "/sys/class/leds/lcd-backlight/brightness";
    }

    //<editor-fold desc="LCD">
    public double lcdBrightness() {
        //screen.full / 255
        return 1.490196078;
    }

    public double lcdBacklight() {
        //screen.on
        // TODO: 11/08/16 add cpu_base?
        return 71.0;
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
    public double[] cpuPowerRatios() {
        return arrayCpuPowerRatios;
    }

    public double[] cpuFreqs() {
        return arrayCpuFreqs;
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
    public double wifiLowPower() {
        return 38.554;
    }

    public double wifiHighPower() {
        return 720;
    }

    //PACKET PER SECONDS
    public double wifiLowHighTransition() {
        return 15;
    }

    public double wifiHighLowTransition() {
        return 8;
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

    public double getMaxPower(String componentName) {
        if ("LCD".equals(componentName)) {
            return lcdBacklight() + lcdBrightness() * 255;
        } else if ("CPU".equals(componentName)) {
            double[] ratios = cpuPowerRatios();
            return ratios[ratios.length - 1] * 100;
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
