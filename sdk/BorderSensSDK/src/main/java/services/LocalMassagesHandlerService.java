package services;

import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProvider;

import java.util.Map;

public interface LocalMassagesHandlerService
{
    Map<String,Object> sendSyncMessage(SecurityProvider securityProvider, String idDevice, String message);
}
