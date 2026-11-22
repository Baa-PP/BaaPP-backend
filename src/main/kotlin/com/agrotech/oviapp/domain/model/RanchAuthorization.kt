package com.agrotech.oviapp.domain.model

enum class RanchPermission(val jsonKey: String) {
    LECTURA("lectura"),
    CAPTURA("captura"),
    EDICION("edicion"),
    EXPORTACION("exportacion"),
}

data class RanchAccess(
    val role: Role,
    val permissions: Map<String, Any?> = emptyMap(),
) {
    fun hasPermission(permission: RanchPermission): Boolean =
        when (role) {
            Role.OWNER,
            Role.ASSOCIATION_SUPERUSER,
            Role.VET_TECHNICIAN,
            -> true
            Role.WORKER_BRANCH -> permissions[permission.jsonKey] == true
        }
}
