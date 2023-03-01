package services;

import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import model.ProvisioningStatus;

import java.util.Map;

public interface BorderSensSDK {
    void initialize();

    Map<String,Object> sendSyncMessage(SecurityProvider securityProvider, String message);

    ProvisioningStatus registerDeviceWithIntermediate(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath);
}
