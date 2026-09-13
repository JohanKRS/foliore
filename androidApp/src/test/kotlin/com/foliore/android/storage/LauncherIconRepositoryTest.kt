package com.foliore.android.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherIconRepositoryTest {
    @Test
    fun `every transition leaves one entry and survives repository recreation`() {
        for (atomic in listOf(true, false)) {
            for (from in LauncherIcon.entries) {
                for (to in LauncherIcon.entries) {
                    val components = FakeComponents(atomic, mutableSetOf(from))
                    val repository = LauncherIconRepository(components)
                    assertEquals(from, repository.icon.value)
                    assertTrue(repository.select(to))
                    assertEquals(setOf(to), components.enabled)
                    assertEquals(to, LauncherIconRepository(components).icon.value)
                    assertTrue(components.snapshots.all { it.isNotEmpty() })
                    if (atomic && from != to) assertEquals(1, components.snapshots.size)
                }
            }
        }
    }

    @Test
    fun `selecting current icon does not notify launcher again`() {
        val components = FakeComponents(true)
        val repository = LauncherIconRepository(components)
        assertTrue(repository.select(LauncherIcon.DAY))
        assertTrue(components.snapshots.isEmpty())
    }

    @Test
    fun `failed atomic update preserves current choice`() {
        val components = FakeComponents(true)
        val repository = LauncherIconRepository(components)
        components.failNextAtomic = true
        assertFalse(repository.select(LauncherIcon.NIGHT))
        assertEquals(setOf(LauncherIcon.DAY), components.enabled)
        assertEquals(LauncherIcon.DAY, repository.icon.value)
    }

    @Test
    fun `failed legacy enable leaves old entry usable`() {
        val components = FakeComponents(false)
        val repository = LauncherIconRepository(components)
        components.failOnce = LauncherIcon.DUSK to true
        assertFalse(repository.select(LauncherIcon.DUSK))
        assertEquals(setOf(LauncherIcon.DAY), components.enabled)
        assertEquals(LauncherIcon.DAY, repository.icon.value)
        assertTrue(components.snapshots.all { it.isNotEmpty() })
    }

    @Test
    fun `failed legacy disable rolls back without leaving no launcher entry`() {
        val components = FakeComponents(false)
        val repository = LauncherIconRepository(components)
        components.failOnce = LauncherIcon.DAY to false
        assertFalse(repository.select(LauncherIcon.NIGHT))
        assertEquals(setOf(LauncherIcon.DAY), components.enabled)
        assertEquals(LauncherIcon.DAY, repository.icon.value)
        assertTrue(components.snapshots.all { it.isNotEmpty() })
        assertTrue(repository.select(LauncherIcon.NIGHT))
    }

    @Test
    fun `opening after interrupted legacy switch repairs duplicate entries`() {
        val components = FakeComponents(false, mutableSetOf(LauncherIcon.DAY, LauncherIcon.NIGHT))
        val repository = LauncherIconRepository(components)
        assertEquals(setOf(repository.icon.value), components.enabled)
        assertTrue(components.snapshots.all { it.isNotEmpty() })
    }

    @Test
    fun `opening from share with no enabled launcher entry restores default`() {
        val components = FakeComponents(false, mutableSetOf())
        val repository = LauncherIconRepository(components)
        assertEquals(LauncherIcon.DAY, repository.icon.value)
        assertEquals(setOf(LauncherIcon.DAY), components.enabled)
        assertTrue(components.snapshots.all { it.isNotEmpty() })
    }

    private class FakeComponents(
        override val supportsAtomicChanges: Boolean,
        val enabled: MutableSet<LauncherIcon> = mutableSetOf(LauncherIcon.DAY),
    ) : LauncherIconComponents {
        val snapshots = mutableListOf<Set<LauncherIcon>>()
        var failOnce: Pair<LauncherIcon, Boolean>? = null
        var failNextAtomic = false
        override fun isEnabled(icon: LauncherIcon) = icon in enabled
        override fun setEnabled(icon: LauncherIcon, enabled: Boolean) {
            if (failOnce == icon to enabled) {
                failOnce = null
                throw SecurityException("Injected component update failure")
            }
            if (enabled) this.enabled.add(icon) else this.enabled.remove(icon)
            snapshots.add(this.enabled.toSet())
        }
        override fun setAll(selected: LauncherIcon) {
            if (failNextAtomic) {
                failNextAtomic = false
                throw SecurityException("Injected batch failure")
            }
            enabled.clear()
            enabled.add(selected)
            snapshots.add(enabled.toSet())
        }
    }
}
