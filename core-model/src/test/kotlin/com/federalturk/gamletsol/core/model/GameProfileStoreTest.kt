package com.federalturk.gamletsol.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfileStoreTest {

    @Test
    fun `upsert then get returns the same profile`() {
        val store = GameProfileStore()
        store.upsert(SampleGameProfiles.ACTION_RPG)

        assertEquals(SampleGameProfiles.ACTION_RPG, store.get("sample-action-rpg"))
    }

    @Test
    fun `removing an unknown profileId does not throw`() {
        val store = GameProfileStore()

        // Kabul kriteri: "profil silme güvenli" — hiç eklenmemiş bir id
        // silinmeye çalışılırsa sessizce no-op olmalı, hata fırlatmamalı.
        store.remove("does-not-exist")

        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `removing an existing profile deletes it and leaves others intact`() {
        val store = GameProfileStore()
        store.upsert(SampleGameProfiles.ACTION_RPG)
        store.upsert(SampleGameProfiles.FPS)

        store.remove(SampleGameProfiles.ACTION_RPG.profileId)

        assertNull(store.get(SampleGameProfiles.ACTION_RPG.profileId))
        assertEquals(SampleGameProfiles.FPS, store.get(SampleGameProfiles.FPS.profileId))
    }

    @Test
    fun `upsert migrates an older schemaVersion up to the current one`() {
        val store = GameProfileStore()
        val legacy = SampleGameProfiles.ACTION_RPG.copy(schemaVersion = 0)

        store.upsert(legacy)

        val migrated = store.get(legacy.profileId)
        assertEquals(GameProfile.CURRENT_SCHEMA_VERSION, migrated?.schemaVersion)
        // Migrasyon diğer alanları değiştirmemeli — sadece şema numarası güncellenir.
        assertEquals(legacy.copy(schemaVersion = GameProfile.CURRENT_SCHEMA_VERSION), migrated)
    }

    @Test
    fun `upsert leaves an already-current schemaVersion untouched`() {
        val store = GameProfileStore()

        store.upsert(SampleGameProfiles.FPS)

        assertEquals(SampleGameProfiles.FPS, store.get(SampleGameProfiles.FPS.profileId))
    }

    @Test
    fun `all three sample genres are distinct and cover RPG, FPS and STRATEGY`() {
        val genres = SampleGameProfiles.ALL.map { it.genre }.toSet()

        assertEquals(setOf(GameGenre.RPG, GameGenre.FPS, GameGenre.STRATEGY), genres)
    }
}
