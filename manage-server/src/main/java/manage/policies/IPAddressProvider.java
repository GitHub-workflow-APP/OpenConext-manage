package manage.policies;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class IPAddressProvider {

    private IPAddressProvider() {
    }

    public static IPInfo getIpInfo(String ipAddress, Integer networkPrefix) {
        InetAddress address;
        try {
            address = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        boolean isIpv4 = address instanceof Inet4Address;
        if (networkPrefix == null) {
            networkPrefix = isIpv4 ? 24 : 64;
        }
        CIDRUtils cidrUtils;
        try {
            cidrUtils = new CIDRUtils(ipAddress.concat("/").concat(networkPrefix.toString()));
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
        int byteSize = isIpv4 ? 32 : 128;
        double capacity = Math.pow(2, byteSize - networkPrefix);
        return new IPInfo(cidrUtils.getNetworkAddress(), cidrUtils.getBroadcastAddress(), capacity, isIpv4, networkPrefix);
    }

}
