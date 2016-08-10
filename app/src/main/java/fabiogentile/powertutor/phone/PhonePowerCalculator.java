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

import fabiogentile.powertutor.components.Audio.AudioData;
import fabiogentile.powertutor.components.CPU.CpuData;
import fabiogentile.powertutor.components.GPS.GpsData;
import fabiogentile.powertutor.components.LCD.LcdData;
import fabiogentile.powertutor.components.OLED.OledData;
import fabiogentile.powertutor.components.Sensors.SensorData;
import fabiogentile.powertutor.components.Threeg.ThreegData;
import fabiogentile.powertutor.components.Wifi.WifiData;

public interface PhonePowerCalculator {
    double getLcdPower(LcdData data);

    double getOledPower(OledData data);

    double getCpuPower(CpuData data);

    double getAudioPower(AudioData data);

    double getGpsPower(GpsData data);

    double getWifiPower(WifiData data);

    double getThreeGPower(ThreegData data);

    double getSensorPower(SensorData data);
}

