package com.agrotech.oviapp.domain.port

import java.util.UUID

interface RanchMembershipPort {
    fun hasAccess(principalIdentifier: String, ranchId: UUID): Boolean
}
