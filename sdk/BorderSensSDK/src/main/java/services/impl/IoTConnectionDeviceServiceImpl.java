package services.impl;

import com.microsoft.azure.sdk.iot.provisioning.device.*;
import com.microsoft.azure.sdk.iot.provisioning.device.internal.exceptions.ProvisioningDeviceClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;
import model.ProvisioningStatus;
import model.SecurityProviderX509Cert;
import services.IoTConnectionDeviceService;
import sdkutils.Utilities;

import java.io.IOException;
import java.security.Key;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;

public class IoTConnectionDeviceServiceImpl implements IoTConnectionDeviceService
{


    private String idScope;
    private String globalEndpoint;
    private ProvisioningDeviceClientTransportProtocol PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL;
    private int MAX_TIME_TO_WAIT_FOR_REGISTRATION; // in milli seconds

    public IoTConnectionDeviceServiceImpl(String idScope, String globalEndpoint)
    {
        this.idScope = idScope;
        this.globalEndpoint = globalEndpoint;
        PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL= ProvisioningDeviceClientTransportProtocol.HTTPS;
        MAX_TIME_TO_WAIT_FOR_REGISTRATION = 10000;
    }

    public IoTConnectionDeviceServiceImpl()
    {
        this.idScope = Utilities.readProperty("iothub.dps.idScope","0ne005D00DC");
        this.globalEndpoint = Utilities.readProperty("iothub.dps.globalendpoint","bs-iothub-service.azure-devices.net");
        PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL= ProvisioningDeviceClientTransportProtocol.HTTPS;
        MAX_TIME_TO_WAIT_FOR_REGISTRATION = Integer.parseInt(Utilities.readProperty("iothub.dps.maxtimeout","10000"));
    }

    @Override
    public ProvisioningStatus registerDeviceWithIntermediate(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath)
    {
        System.out.println("Starting...");
        System.out.println("Beginning setup.");
        ProvisioningDeviceClient provisioningDeviceClient = null;
        ProvisioningStatus provisioningStatus = new ProvisioningStatus();
        try
        {
            SecurityProvider securityProviderX509 = getSecurityProviderX509(publicDeviceCertificatePath,privateDeviceCertificatePath,publicIntermediateCertificatePath);

            provisioningDeviceClient = ProvisioningDeviceClient.create(globalEndpoint, idScope, PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL,
                    securityProviderX509);

            provisioningDeviceClient.registerDevice(new ProvisioningDeviceClientRegistrationCallback()
            {
                @Override
                public void run(ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationResult, Exception e, Object context)
                {
                    if (context instanceof ProvisioningStatus)
                    {
                        ProvisioningStatus status = (ProvisioningStatus) context;
                        status.setProvisioningDeviceClientRegistrationInfoClient(provisioningDeviceClientRegistrationResult);
                        status.setException(e);
                        status.setSecurityProviderX509(securityProviderX509);
                    }
                    else
                    {
                        System.out.println("Received unknown context");
                    }
                }
            }, provisioningStatus);

            while (provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getProvisioningDeviceClientStatus() != ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED)
            {
                if (provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ERROR ||
                        provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_DISABLED ||
                        provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_FAILED )

                {
                    provisioningStatus.getException().printStackTrace();
                    System.out.println("Registration error, bailing out");
                    break;
                }
                System.out.println("Waiting for Provisioning Service to register");
                Thread.sleep(MAX_TIME_TO_WAIT_FOR_REGISTRATION);
            }
            if (provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED)
            {
                System.out.println("IotHUb Uri : " + provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getIothubUri());
                System.out.println("Device ID : " + provisioningStatus.getProvisioningDeviceClientRegistrationInfoClient().getDeviceId());
                provisioningStatus.setProvisioned(true);

            }

        } catch (IOException e) {
            provisioningStatus.setException(e);
            e.printStackTrace();
        }
        catch (CertificateException e)
        {
            provisioningStatus.setException(e);
            e.printStackTrace();
        }
        catch (ProvisioningDeviceClientException e)
        {
            provisioningStatus.setException(e);
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            provisioningStatus.setException(e);
            e.printStackTrace();
        }

        System.out.println("Register Device done");
        return provisioningStatus;
    }

    public static SecurityProvider getSecurityProviderX509(String publicDeviceCertificatePath, String privateDeviceCertificatePath, String publicIntermediateCertificatePath) throws IOException, CertificateException
    {
        String leafPublicPem = Utilities.readCertificateFromPath(publicDeviceCertificatePath);
        String leafPrivateKeyPem = Utilities.readCertificateFromPath(privateDeviceCertificatePath);

        X509Certificate leafPublicCert = Utilities.parsePublicKeyCertificate(leafPublicPem);
        Key leafPrivateKey = Utilities.parsePrivateKey(leafPrivateKeyPem);
        SecurityProvider securityProviderX509;
        if (publicIntermediateCertificatePath!=null)
        {
            String intermediateKey = Utilities.readCertificateFromPath(publicIntermediateCertificatePath);
            X509Certificate intermediatePublicCert = Utilities.parsePublicKeyCertificate(intermediateKey);
            securityProviderX509 = new SecurityProviderX509Cert(leafPublicCert, leafPrivateKey, intermediatePublicCert);
        } else {
            securityProviderX509 = new SecurityProviderX509Cert(leafPublicCert, leafPrivateKey, new ArrayList());
        }
        return securityProviderX509;
    }

    public static SecurityProvider getSecurityProviderX509(String publicDeviceCertificatePath, String privateDeviceCertificatePath) throws IOException, CertificateException
    {
        String leafPublicPem = Utilities.readCertificateFromPath(publicDeviceCertificatePath);
        String leafPrivateKeyPem = Utilities.readCertificateFromPath(privateDeviceCertificatePath);

        X509Certificate leafPublicCert = Utilities.parsePublicKeyCertificate(leafPublicPem);
        Key leafPrivateKey = Utilities.parsePrivateKey(leafPrivateKeyPem);
        SecurityProvider securityProviderX509= new SecurityProviderX509Cert(leafPublicCert, leafPrivateKey, new ArrayList());
        return securityProviderX509;
    }
}
