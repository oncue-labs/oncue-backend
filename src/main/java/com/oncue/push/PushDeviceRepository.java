package com.oncue.push;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
