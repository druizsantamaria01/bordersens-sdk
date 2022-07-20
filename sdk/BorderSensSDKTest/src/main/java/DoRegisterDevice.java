import model.ProvisioningStatus;
import services.IoTConnectionDeviceService;
import services.impl.IoTConnectionDeviceServiceImpl;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;


public class DoRegisterDevice {
    public static void main(String[] args) {
        String idScope = "0ne005D00DC";
        String globalEndpoint = "global.azure-devices-provisioning.net";
        IoTConnectionDeviceService rds  = new IoTConnectionDeviceServiceImpl(idScope,globalEndpoint);
        try
        {
            //URL folderURL = SendMessage.class.getClassLoader().getResource("certificates");
            //File folder = new File(folderURL.getFile());
            String certificatesPath = "C:\\Users\\druiz\\repositorios\\BorderSens\\bordersens-sdk\\Certificates";
            String publicCertificate = certificatesPath + "\\device-2a-bs-public.pem";//"/new-device-01.cert.pem";
            String privateCertificate = certificatesPath + "\\device-2a-bs-private.pem";//"/new-device-01.key.pem";
            String intermediateCertificate = certificatesPath + "\\Intermediate1-bs-group-2.pem";//"/azure-iot-test-only.intermediate.cert.pem";
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
