package com.andrerinas.openheadunit.connection

/** Device-wide P2P broadcasts describe the radio, not the app that created its group. */
internal class P2pSessionOwnership(recordedName: String? = null) {
    var requested = false
        private set
    var enabled = true
        private set
    @Volatile var generation = 0
        private set
    var created = false
        private set
    var networkName: String? = recordedName
        private set

    fun start() { if (!requested) generation++; requested = true }
    fun stop() { requested = false; generation++; created = false }
    fun setEnabled(value: Boolean) {
        if (enabled && !value) { generation++; created = false }
        enabled = value
    }
    fun current(token: Int) = requested && enabled && token == generation
    fun beginCreate() { created = false }
    fun expect(name: String) { networkName = name; created = false }
    fun created(token: Int): Boolean {
        if (!current(token)) return false
        created = true
        return true
    }
    fun owns(isOwner: Boolean, name: String?): Boolean =
        isOwner && !name.isNullOrBlank() && name == networkName

    // Legacy createGroup cannot choose a name. Adopt it only after our create succeeded.
    fun observeCreatedGroup(isOwner: Boolean, name: String?) {
        if (created && networkName == null && isOwner && !name.isNullOrBlank()) networkName = name
    }
    fun expectSystemName() { networkName = null; created = false }
}
