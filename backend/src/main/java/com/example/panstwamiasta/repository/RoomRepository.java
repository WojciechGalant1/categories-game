package com.example.panstwamiasta.repository;

import com.example.panstwamiasta.room.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, String> {

    @Query("SELECT r FROM Room r WHERE r.isPublic = true AND r.status = 'lobby'")
    List<Room> findPublicLobbies();

    // Atomic, multi-replica-safe auto-stop: only one replica can flip a given row
    // from "playing" to "reviewing" once the round timer has expired.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Room r SET r.status = :reviewing " +
           "WHERE r.code = :code AND r.status = :playing AND r.game.roundEndsAt <= :now")
    int autoStopIfExpired(@Param("code") String code,
                          @Param("now") long now,
                          @Param("playing") Room.RoomStatus playing,
                          @Param("reviewing") Room.RoomStatus reviewing);
}

