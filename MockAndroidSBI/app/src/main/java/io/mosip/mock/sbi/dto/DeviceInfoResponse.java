package io.mosip.mock.sbi.dto;

public class DeviceInfoResponse {

    public String deviceInfo;
    public Error error;
    /** SAJEEV: this device is a mock. Always true in this app. */
    public boolean mock = true;
    public DeviceInfoResponse() { }
    public DeviceInfoResponse(String deviceInfo, Error error) {
        this.deviceInfo = deviceInfo;
        this.error = error;
    }

    @Override
    public String toString() {
        return "DeviceInfoResponse{" +
                "deviceInfo='" + deviceInfo + '\'' +
                ", error=" + error +
                '}';
    }
}
