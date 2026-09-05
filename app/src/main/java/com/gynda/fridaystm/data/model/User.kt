package com.gynda.fridaystm.data.model

import com.gynda.fridaystm.domain.Role
import com.gynda.fridaystm.domain.roleFromWire
import com.gynda.fridaystm.util.UserRole

/**
 * A student (or staff) profile — Firestore document `users/{uid}`.
 *
 * Firestore DTO: every property has a default value, which is what lets the
 * Firestore SDK instantiate it via the generated no-arg constructor during
 * `toObject<User>()`. The Kotlin property names already match the wire field
 * names, so no `@PropertyName` remapping is needed here.
 *
 * @property uid Firebase Auth uid; also the document id of this profile.
 * @property nis Nomor Induk Siswa.
 * @property grade Student grade: 10 | 11 | 12 (0 = unknown / not set).
 * @property role One of [UserRole]; students may never self-assign a privileged
 *   role (enforced by the Firestore security rules, not the client).
 */
data class User(
    val uid: String = "",
    val nis: String = "",
    val nama: String = "",
    val grade: Int = 0,
    val kelas: String = "",
    val role: String = UserRole.STUDENT,
    val photoUrl: String = "",
) {
    /** Typed [Role] for RBAC gating; unknown/blank wire value fails safe to student. */
    @get:com.google.firebase.firestore.Exclude
    val roleEnum: Role
        get() = roleFromWire(role)
}
