package com.agrotech.oviapp.notifications.infrastructure

import com.agrotech.oviapp.domain.exception.ResourceNotFoundException
import com.agrotech.oviapp.infrastructure.persistence.UsuarioJpaRepository
import com.agrotech.oviapp.infrastructure.persistence.entity.AnimalEntity
import com.agrotech.oviapp.infrastructure.persistence.entity.UsuarioEntity
import com.agrotech.oviapp.notifications.domain.DeviceTokenPort
import com.agrotech.oviapp.notifications.domain.DueWeaningLamb
import com.agrotech.oviapp.notifications.domain.WeaningAlertPort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface DueLambRow {
    fun getAnimalId(): UUID
    fun getRanchId(): UUID
    fun getFolioInterno(): String
}

interface DeviceTokenJpaRepository : JpaRepository<UsuarioEntity, UUID> {
    @Modifying
    @Query(
        value = """
            DELETE FROM dispositivos_fcm
            WHERE token = :token AND NOT (usuario_id = :userId AND dispositivo_id = :deviceId)
        """,
        nativeQuery = true,
    )
    fun deleteTokenFromOtherDevices(
        @Param("token") token: String,
        @Param("userId") userId: UUID,
        @Param("deviceId") deviceId: String,
    ): Int

    @Modifying
    @Query(
        value = """
            INSERT INTO dispositivos_fcm (id, usuario_id, dispositivo_id, token)
            VALUES (:id, :userId, :deviceId, :token)
            ON CONFLICT (usuario_id, dispositivo_id)
            DO UPDATE SET token = EXCLUDED.token, actualizado_en = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("deviceId") deviceId: String,
        @Param("token") token: String,
    ): Int

    @Query(
        value = """
            SELECT d.token
            FROM dispositivos_fcm d
            JOIN usuarios u ON u.id = d.usuario_id
            WHERE u.rancho_id = :ranchId
        """,
        nativeQuery = true,
    )
    fun findTokensByRanchId(@Param("ranchId") ranchId: UUID): List<String>
}

interface WeaningAlertJpaRepository : JpaRepository<AnimalEntity, UUID> {
    @Query(
        value = """
            SELECT a.id            AS animalId,
                   a.rancho_id     AS ranchId,
                   a.folio_interno AS folioInterno
            FROM animales a
            JOIN ranchos r ON r.id = a.rancho_id
            WHERE a.estado = 'ACTIVE'
              AND a.etapa_vida = 'LAMB_LACTATING'
              AND a.fecha_destete IS NULL
              AND CURRENT_DATE - a.fecha_nacimiento >= r.dias_meta_destete
              AND NOT EXISTS (SELECT 1 FROM alertas_destete_enviadas e WHERE e.animal_id = a.id)
            ORDER BY a.rancho_id, a.folio_interno
        """,
        nativeQuery = true,
    )
    fun findDueLambs(): List<DueLambRow>

    @Modifying
    @Query(
        value = """
            INSERT INTO alertas_destete_enviadas (animal_id)
            SELECT a.id FROM animales a WHERE a.id IN (:animalIds)
            ON CONFLICT (animal_id) DO NOTHING
        """,
        nativeQuery = true,
    )
    fun markSent(@Param("animalIds") animalIds: List<UUID>): Int
}

@Component
class DeviceTokenJpaAdapter(
    private val repository: DeviceTokenJpaRepository,
    private val usuarioRepository: UsuarioJpaRepository,
) : DeviceTokenPort {

    override fun register(username: String, deviceId: String, token: String) {
        val userId = usuarioRepository.findByUsuario(username)?.id
            ?: throw ResourceNotFoundException("USER_NOT_FOUND", "Usuario autenticado no encontrado.")
        // Un token pertenece a un solo dispositivo: si cambió de usuario o dispositivo, se libera antes de guardar.
        repository.deleteTokenFromOtherDevices(token, userId, deviceId)
        repository.upsert(UUID.randomUUID(), userId, deviceId, token)
    }

    override fun findTokensByRanch(ranchId: UUID): List<String> = repository.findTokensByRanchId(ranchId)
}

@Component
class WeaningAlertJpaAdapter(
    private val repository: WeaningAlertJpaRepository,
) : WeaningAlertPort {

    @Transactional(readOnly = true)
    override fun findDueLambs(): List<DueWeaningLamb> =
        repository.findDueLambs().map {
            DueWeaningLamb(animalId = it.getAnimalId(), ranchId = it.getRanchId(), folioInterno = it.getFolioInterno())
        }

    // Transacción propia: el caso de uso no es transaccional porque envía push (I/O externa) entre ranchos.
    @Transactional
    override fun markSent(animalIds: List<UUID>) {
        if (animalIds.isNotEmpty()) repository.markSent(animalIds)
    }
}
