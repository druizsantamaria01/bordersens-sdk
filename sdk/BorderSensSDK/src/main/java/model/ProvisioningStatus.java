package model;

import com.microsoft.azure.sdk.iot.provisioning.device.ProvisioningDeviceClientRegistrationResult;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;

public class ProvisioningStatus
{
    private ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationInfoClient = new ProvisioningDeviceClientRegistrationResult();
    private Exception exception;
    private SecurityProvider securityProviderX509;
    private boolean isProvisioned = false;

    public ProvisioningStatus()
    {
    }

    public ProvisioningDeviceClientRegistrationResult getProvisioningDeviceClientRegistrationInfoClient()
    {
        return provisioningDeviceClientRegistrationInfoClient;
    }

    public void setProvisioningDeviceClientRegistrationInfoClient(ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationInfoClient)
    {
        this.provisioningDeviceClientRegistrationInfoClient = provisioningDeviceClientRegistrationInfoClient;
    }

    public Exception getException()
    {
        return exception;
    }

    public void setException(Exception exception)
    {
        this.exception = exception;
    }

    public SecurityProvider getSecurityProviderX509()
    {
        return securityProviderX509;
    }

    public void setSecurityProviderX509(SecurityProvider securityProviderX509)
    {
        this.securityProviderX509 = securityProviderX509;
    }

    public boolean isProvisioned()
    {
        return isProvisioned;
    }

    public void setProvisioned(boolean provisioned)
    {
        isProvisioned = provisioned;
    }
}
