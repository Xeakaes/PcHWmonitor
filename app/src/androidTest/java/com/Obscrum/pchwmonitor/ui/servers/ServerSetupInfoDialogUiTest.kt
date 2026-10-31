package com.Obscrum.pchwmonitor.ui.servers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.Obscrum.pchwmonitor.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerSetupInfoDialogUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var dismissCount = 0

    /** Seed value the clipboard is primed with so the copy-link assertion can only pass via the feature. */
    private val clipboardSentinel = "clipboard-sentinel-not-the-guide-url"

    private val step1: String get() = compose.activity.getString(R.string.setup_info_step1)
    private val step2: String get() = compose.activity.getString(R.string.setup_info_step2)
    private val step3: String get() = compose.activity.getString(R.string.setup_info_step3)

    @Before
    fun setUp() {
        dismissCount = 0

        // Instrumentation does not reset the system clipboard, and a prior run (or a
        // manual copy) can leave exactly the guide URL on it. Prime it with a sentinel
        // so the post-click assertion in copyLinkPutsGuideUrlOnClipboard can only pass
        // when the feature actually writes the clipboard.
        val clipboard =
            compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sentinel", clipboardSentinel))
        assertEquals("Clipboard primed with sentinel", clipboardSentinel, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    private fun setDialog() {
        compose.setContent {
            MaterialTheme {
                ServerSetupInfoDialog(onDismiss = { dismissCount++ })
            }
        }
    }

    @Test
    fun dialogShowsTitleAndSteps() {
        setDialog()

        compose.onNodeWithText(compose.activity.getString(R.string.setup_info_title))
            .assertIsDisplayed()
        compose.onNode(hasText(step1, substring = true)).assertIsDisplayed()
        compose.onNode(hasText(step2, substring = true)).assertIsDisplayed()
        compose.onNode(hasText(step3, substring = true)).assertIsDisplayed()
    }

    @Test
    fun gotItInvokesOnDismiss() {
        setDialog()

        compose.onNodeWithText(compose.activity.getString(R.string.setup_info_got_it))
            .performClick()

        compose.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun dismissRequestInvokesOnDismiss() {
        setDialog()

        // The Compose AlertDialog lives in its own dialog window. Wait until it is
        // attached and idle before Espresso picks a root: pressing back too early
        // makes Espresso latch onto the activity window, which then loses focus to
        // the dialog and the root picker times out. With the dialog settled, the
        // picker selects the focused dialog window and the injected back key
        // reaches the dialog's onDismissRequest.
        compose.onNodeWithText(compose.activity.getString(R.string.setup_info_title))
            .assertIsDisplayed()
        Espresso.onIdle()

        Espresso.pressBack()

        compose.runOnIdle {
            assertEquals(1, dismissCount)
        }
    }

    @Test
    fun copyLinkPutsGuideUrlOnClipboard() {
        setDialog()

        // Confirm the sentinel is still in place before clicking: the post-click
        // assertion below can then only be satisfied by the Copy-link handler.
        val clipboard =
            compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(
            "Clipboard must hold the sentinel before the click",
            clipboardSentinel,
            clipboard.primaryClip?.getItemAt(0)?.text?.toString(),
        )

        compose.onNodeWithText(compose.activity.getString(R.string.setup_info_copy_link))
            .performClick()

        compose.runOnIdle {
            val clip = clipboard.primaryClip
            assertTrue("Clipboard should contain one item", clip != null && clip.itemCount == 1)
            assertEquals(
                "https://xeakaes.github.io/PcHWmonitor/",
                clip!!.getItemAt(0).text?.toString(),
            )
        }
    }
}

@RunWith(AndroidJUnit4::class)
class ServersScreenHelpIconTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var helpClicks = 0

    private val setupHelpLabel: String
        get() = compose.activity.getString(R.string.setup_help_description)

    @Test
    fun helpIconOpensDialog() {
        compose.setContent {
            MaterialTheme {
                ServersScreen(
                    servers = emptyList(),
                    activeServerId = null,
                    connectionState = null,
                    onBack = {},
                    onSelect = {},
                    onEdit = {},
                    onDelete = {},
                    onQrPayload = {},
                    onAddManual = {},
                    onShowSetupHelp = { helpClicks++ },
                )
            }
        }

        compose.onNodeWithContentDescription(setupHelpLabel)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        compose.runOnIdle {
            assertEquals(1, helpClicks)
        }
    }
}
