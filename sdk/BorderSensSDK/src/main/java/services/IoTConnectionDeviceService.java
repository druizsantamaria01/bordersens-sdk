package services;

import model.ProvisioningStatus;

public interface IoTConnectionDeviceService
{
    public ProvisioningStatus registerDeviceWithIntermediate(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath);

}
