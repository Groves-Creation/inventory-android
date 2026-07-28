package com.brother.sdk.lmprinter;

import java.util.Map;

public class Channel {
    public enum ChannelType { USB, Wifi, Bluetooth, BluetoothLowEnergy }

    public enum ExtraInfoKey {
        ModelName, SerialNubmer, MACAddress, NodeName, Location, BluetoothAlias, AdvertiseLocalName, IpAddress
    }

    public static Channel newWifiChannel(final String ipAddress) { return null; }

    public ChannelType getChannelType() { return null; }

    public String getChannelInfo() { return null; }

    public Map<ExtraInfoKey, String> getExtraInfo() { return null; }
}
