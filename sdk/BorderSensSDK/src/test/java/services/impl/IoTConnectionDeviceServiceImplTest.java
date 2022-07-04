package services.impl;

import model.ProvisioningStatus;
import org.junit.jupiter.api.Test;
import services.IoTConnectionDeviceService;

import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IoTConnectionDeviceServiceImplTest {

    @Test
    void getInstance()
    {
    }

    @Test
    void registerDeviceWithIntermediate()
    {
        String idScope = "0ne005D00DC";
        String globalEndpoint = "global.azure-devices-provisioning.net";
        IoTConnectionDeviceService rds  = new IoTConnectionDeviceServiceImpl(idScope,globalEndpoint);
        try
        {
            String certificatesPath = Paths.get(getClass().getClassLoader().getResource("./certificates").toURI()).toAbsolutePath().toString();
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            String intermediateCertificate = certificatesPath + "\\Intermediate1-bs-group.pem";//"/azure-iot-test-only.intermediate.cert.pem";
            ProvisioningStatus provisioningStatus = rds.registerDeviceWithIntermediate(publicCertificate,privateCertificate,intermediateCertificate);
            assertTrue(provisioningStatus!=null && provisioningStatus.isProvisioned());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}