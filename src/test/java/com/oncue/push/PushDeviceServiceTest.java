package com.oncue.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDeviceServiceTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    @Mock
    private UserRepository userRepository;

    @Test
    void registersOneActiveDeviceByReplacingTheExistingToken() {
        User user = new User(7L);
        PushDevice existingDevice = new PushDevice(
                user, "old-token", PushPlatform.IOS, PushEnvironment.SANDBOX);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByUserId(7L)).thenReturn(Optional.of(existingDevice));
        when(pushDeviceRepository.save(existingDevice)).thenReturn(existingDevice);

        PushDeviceService service = newService();
        PushDeviceResponse response = service.register(
                7L,
                new RegisterPushDeviceRequest("new-token", "IOS", "PRODUCTION"));

        assertThat(existingDevice.getDeviceToken()).isEqualTo("new-token");
        assertThat(existingDevice.getEnvironment()).isEqualTo(PushEnvironment.PRODUCTION);
        assertThat(response.deviceToken()).isEqualTo("new-token");
        verify(pushDeviceRepository).save(existingDevice);
    }

    @Test
    void createsTheFirstDeviceForAUser() {
        User user = new User(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(pushDeviceRepository.findByUserId(7L)).thenReturn(Optional.empty());
        when(pushDeviceRepository.save(any(PushDevice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PushDeviceResponse response = newService().register(
                7L,
                new RegisterPushDeviceRequest("device-token", "IOS", "SANDBOX"));

        assertThat(response.deviceToken()).isEqualTo("device-token");
        assertThat(response.platform()).isEqualTo(PushPlatform.IOS);
        assertThat(response.environment()).isEqualTo(PushEnvironment.SANDBOX);
    }

    @Test
    void deletesTheCurrentDeviceDuringLogout() {
        newService().delete(7L);

        verify(pushDeviceRepository).deleteByUserId(7L);
    }

    private PushDeviceService newService() {
        return new PushDeviceService(pushDeviceRepository, userRepository);
    }
}
