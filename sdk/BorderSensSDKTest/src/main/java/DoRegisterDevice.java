import model.ProvisioningStatus;
import services.IoTConnectionDeviceService;
import services.impl.IoTConnectionDeviceServiceImpl;

import java.nio.file.Paths;


public class DoRegisterDevice {
    public static void main(String[] args) {
        String idScope = "0ne005D00DC";
        String globalEndpoint = "global.azure-devices-provisioning.net";
        IoTConnectionDeviceService rds  = new IoTConnectionDeviceServiceImpl(idScope,globalEndpoint);
        try
        {
            String certificatesPath = "C:\\Users\\danie\\repositorios\\BorderSens\\create-certificates\\azure-iot-sdk-c\\tools\\CACertificates";
            String publicCertificate = certificatesPath + "\\device-1-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-1-bs-private.pem";//"/new-device-01.key.pem";
            String intermediateCertificate = certificatesPath + "\\Intermediate1-bs-group.pem";//"/azure-iot-test-only.intermediate.cert.pem";
            ProvisioningStatus provisioningStatus = rds.registerDeviceWithIntermediate(publicCertificate,privateCertificate,intermediateCertificate);
            if (provisioningStatus!=null && provisioningStatus.isProvisioned()) {
                System.out.println("Registered device");
            } else {
                System.out.println("No registered device");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
