package com.oncue.push;

import com.oncue.auth.model.User;
import com.oncue.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceService {

    private final PushDeviceRepository pushDeviceRepository;
    private final UserRepository userRepository;

    public PushDeviceService(
            PushDeviceRepository pushDeviceRepository,
            UserRepository userRepository) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PushDeviceResponse register(Long userId, RegisterPushDeviceRequest request) {
        PushPlatform platform = parsePlatform(request.platform());
        PushEnvironment environment = parseEnvironment(request.environment());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        PushDevice pushDevice = pushDeviceRepository.findByUserId(userId)
                .orElseGet(() -> new PushDevice(
                        user, request.deviceToken(), platform, environment));
        if (pushDevice.getDeviceToken() != null) {
            pushDevice.replace(request.deviceToken(), platform, environment);
        }
        return toResponse(pushDeviceRepository.save(pushDevice));
    }

    @Transactional
    public void delete(Long userId) {
        pushDeviceRepository.deleteByUserId(userId);
    }

    private static PushPlatform parsePlatform(String value) {
        try {
            return PushPlatform.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported push platform", exception);
        }
    }

    private static PushEnvironment parseEnvironment(String value) {
        try {
            return PushEnvironment.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unsupported APNs environment", exception);
        }
    }

    private static PushDeviceResponse toResponse(PushDevice pushDevice) {
        return new PushDeviceResponse(
                pushDevice.getDeviceToken(),
                pushDevice.getPlatform(),
                pushDevice.getEnvironment(),
                pushDevice.getCreatedAt(),
                pushDevice.getUpdatedAt());
    }
}
