package com.agrotech.oviapp.domain.port

import com.agrotech.oviapp.domain.model.RanchAccess
import java.util.UUID

interface RanchMembershipPort {
    fun resolveAccess(principalIdentifier: String, ranchId: UUID): RanchAccess?

    fun hasAccess(principalIdentifier: String, ranchId: UUID): Boolean =
        resolveAccess(principalIdentifier, ranchId) != null
}
