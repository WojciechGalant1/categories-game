package com.example.panstwamiasta.service;

import com.example.panstwamiasta.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomTtlService roomTtlService;

    @Transactional
    public void deleteRoom(String code) {
        if (roomRepository.existsById(code)) {
            roomRepository.deleteById(code);
            log.info("Deleted room {} after Redis TTL expiry", code);
        } else {
            log.debug("Room {} already deleted, cleaning up Redis keys", code);
        }
        roomTtlService.cleanup(code);
    }
}
