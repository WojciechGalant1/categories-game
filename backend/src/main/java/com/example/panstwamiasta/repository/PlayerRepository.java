package com.example.panstwamiasta.repository;

import com.example.panstwamiasta.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PlayerRepository extends JpaRepository<Player, UUID> {

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Player p "
            + "WHERE p.roomCode = :code AND p.id = :playerId")
    boolean isPlayerInRoom(@Param("code") String code, @Param("playerId") UUID playerId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Player p "
            + "WHERE p.roomCode = :code AND p.id = :playerId AND p.isHost = true")
    boolean isCurrentHost(@Param("code") String code, @Param("playerId") UUID playerId);
}
