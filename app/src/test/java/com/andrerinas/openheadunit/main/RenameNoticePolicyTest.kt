package com.andrerinas.openheadunit.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameNoticePolicyTest {

    private val release = RenameNoticePolicy.RENAME_RELEASE_MS
    private val cutoff = release + RenameNoticePolicy.NEW_USER_WINDOW_MS

    @Test
    fun `existing user installed before the release sees it`() {
        assertTrue(RenameNoticePolicy.shouldOffer(release - 1000))
    }

    @Test
    fun `new install within the window sees it`() {
        assertTrue(RenameNoticePolicy.shouldOffer(release + 1000))
    }

    @Test
    fun `new install after the window does not see it`() {
        assertFalse(RenameNoticePolicy.shouldOffer(cutoff + 1000))
    }

    @Test
    fun `window cutoff is exclusive`() {
        assertFalse(RenameNoticePolicy.shouldOffer(cutoff))
    }

    @Test
    fun `unknown or unset install time falls on the show side`() {
        assertTrue(RenameNoticePolicy.shouldOffer(0L))
    }
}
