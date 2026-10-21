package com.m57.hermescontrol.ui.chat.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.ws.CommandCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Interaction coverage for the chat composer controls: the morphing action
 * button (dictate / send), stopping dictation from either mic position, and
 * both halves of the combined model/reasoning pill.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class ComposerInteractionTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var micTaps = 0
    private var sends = 0
    private var modelTaps = 0
    private var selectedLevel: String? = null
    private var cameraTaps = 0
    private var photosTaps = 0
    private var fileTaps = 0

    /** Renders the real input bar with live text and a mic that toggles like ChatMediaLaunchers. */
    @OptIn(ExperimentalFoundationApi::class)
    private fun setComposer(
        reasoningLevel: String? = "medium",
        model: String = "openai/gpt-5.5",
        composerWidth: Dp? = null,
        modelAfterTap: String? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        showModelProvider: Boolean = false,
    ) {
        composeTestRule.setContent {
            val input = rememberTextFieldState()
            var listening by remember { mutableStateOf(false) }
            var currentModel by remember { mutableStateOf(model) }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                val composer: @Composable () -> Unit = {
                    ChatInputBar(
                        inputState = input,
                        onSend = {
                            sends++
                            input.clearText()
                        },
                        onMicTap = {
                            micTaps++
                            listening = !listening
                        },
                        isListening = listening,
                        isAgentTyping = false,
                        isConnected = true,
                        isSessionReady = true,
                        commandCatalog = CommandCatalog(),
                        currentSessionModel = currentModel,
                        reasoningLevel = reasoningLevel,
                        onModelTap = {
                            modelTaps++
                            modelAfterTap?.let { currentModel = it }
                        },
                        onReasoningTap = { selectedLevel = it },
                        onCameraTap = { cameraTaps++ },
                        onImageTap = { photosTaps++ },
                        onFileTap = { fileTaps++ },
                        showModelProvider = showModelProvider,
                    )
                }
                if (composerWidth == null) {
                    composer()
                } else {
                    // requiredWidth, not width: the CI emulator's screen is
                    // 320dp wide, so plain width() clamps to 320 and the
                    // eight-control toolbar squeezes the model chip's swipeable
                    // viewport to touch-slop size. 420 deliberately overhangs
                    // the screen so the chip keeps a real swipe lane; the mic
                    // button lands completely offscreen (fine for these
                    // assertions — its visibility is covered by other tests).
                    Box(Modifier.requiredWidth(composerWidth).testTag("composer_toolbar")) { composer() }
                }
            }
        }
    }

    private fun openAttachmentTray() {
        composeTestRule.onNodeWithTag("attachment_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithTag("attachment_tray").assertIsDisplayed()
    }

    /**
     * Dumps the clipped on-screen bounds of every pill/toolbar slot. The batch-4
     * swipe tests fail as tap-instead-of-scroll; this string decides whether the
     * model chip's swipeable viewport collapsed to touch-slop size or whether the
     * gesture itself is being misrouted.
     */
    private fun toolbarGeometry(): String {
        val tags =
            listOf(
                "composer_toolbar",
                "attachment_button",
                "model_chip",
                "reasoning_chip",
                "new_chat_button",
                "model_manager_button",
                "sessions_button",
            )
        val rows = mutableListOf<String>()
        for (tag in tags) {
            val nodes = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            val node = nodes.firstOrNull() ?: continue
            rows.add("$tag bounds=${node.boundsInRoot} size=${node.size}")
        }
        return rows.joinToString(" | ")
    }

    private fun closeAttachmentTray() {
        composeTestRule.onNodeWithTag("attachment_button").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.onNodeWithTag("attachment_tray").assertDoesNotExist()
    }

    @Test
    fun emptyInput_actionButtonStartsDictation() {
        setComposer()

        composeTestRule.onNodeWithTag("send_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mic_button").performClick()

        composeTestRule.runOnIdle {
            assertEquals("action button must start dictation", 1, micTaps)
            assertEquals("action button must not send without input", 0, sends)
        }
    }

    @Test
    fun typedInput_actionButtonSends_andMicStillDictates() {
        setComposer()

        composeTestRule.onNodeWithTag("chat_input").performTextInput("Hello Hermes")
        composeTestRule.onNodeWithTag("send_button").performClick()
        composeTestRule.runOnIdle {
            assertEquals("send button must send", 1, sends)
            assertEquals("send must not trigger dictation", 0, micTaps)
        }

        composeTestRule.onNodeWithTag("chat_input").performTextInput("Again")
        composeTestRule.onNodeWithTag("mic_button").performClick()
        composeTestRule.runOnIdle {
            assertEquals("mic next to send must start dictation", 1, micTaps)
            assertEquals("mic must not send", 1, sends)
        }
    }

    @Test
    fun emptyInput_stopButtonStopsDictation() {
        setComposer()

        composeTestRule.onNodeWithTag("mic_button").performClick()
        composeTestRule.onNodeWithTag("mic_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mic_stop_button").performClick()

        composeTestRule.onNodeWithTag("mic_stop_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("mic_button").assertIsDisplayed()
        composeTestRule.runOnIdle { assertEquals("start + stop must both reach onMicTap", 2, micTaps) }
    }

    @Test
    fun typedInput_stopButtonStopsDictation_withoutSending() {
        setComposer()

        composeTestRule.onNodeWithTag("chat_input").performTextInput("Hello Hermes")
        composeTestRule.onNodeWithTag("mic_button").performClick()
        composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_stop_button").performClick()

        composeTestRule.onNodeWithTag("mic_button").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals("start + stop must both reach onMicTap", 2, micTaps)
            assertEquals("stopping dictation must not send", 0, sends)
        }
    }

    @Test
    fun modelSideOfPill_opensModelPicker_only() {
        // Width pinned so the eight-control toolbar leaves the model chip tappable.
        setComposer(composerWidth = 420.dp)

        composeTestRule.onNodeWithTag("model_chip").performClick()

        composeTestRule.onNodeWithText("REASONING").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals("model side must open the picker", 1, modelTaps)
            assertEquals("model side must not change reasoning", null, selectedLevel)
        }
    }

    @Test
    fun reasoningSideOfPill_opensMenu_andSelectsLevel() {
        setComposer(reasoningLevel = "medium")

        composeTestRule.onNodeWithTag("reasoning_chip").performClick()
        composeTestRule.onNodeWithText("REASONING").assertIsDisplayed()
        composeTestRule.onNodeWithText("High").performClick()

        composeTestRule.onNodeWithText("REASONING").assertDoesNotExist()
        composeTestRule.runOnIdle {
            assertEquals("reasoning menu must report the picked level", "high", selectedLevel)
            assertEquals("reasoning side must not open the model picker", 0, modelTaps)
        }
    }

    @Test
    fun longModelName_remainsScrollableAndDoesNotTriggerPicker() {
        val longModel = "openrouter/some-extremely-long-model-name-preview-2026-with-extra-characters"
        // The pill renders the leaf segment only (batch-5B B1), regardless of
        // the provider-display setting — so the visible text is the leaf.
        val longLeaf = longModel.substringAfterLast('/')
        // 420.dp via requiredWidth (see setComposer): enough pill width that the
        // model chip's swipe lane clears touch slop; mic is fully offscreen, so
        // it is no longer asserted here (mic visibility is covered by other
        // tests; the overhang is deliberate).
        setComposer(model = longModel, composerWidth = 420.dp)

        composeTestRule.onNodeWithTag("reasoning_chip").assertIsDisplayed()
        val modelText = composeTestRule.onNodeWithText(longLeaf)
        val leftBefore = modelText.getUnclippedBoundsInRoot().left

        val chipInteraction = composeTestRule.onNodeWithTag("model_chip")
        chipInteraction.performTouchInput { swipeLeft() }
        val leftAfter = modelText.getUnclippedBoundsInRoot().left
        val chipNode = chipInteraction.fetchSemanticsNode()
        val scrollAxis = chipNode.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val diag =
            "taps=$modelTaps leftBefore=$leftBefore leftAfter=$leftAfter " +
                "chipPos=${chipNode.positionInRoot} chipSize=${chipNode.size} " +
                "scrollAxis=$scrollAxis geo=${toolbarGeometry()}"

        composeTestRule.runOnIdle {
            assertEquals("swiping the model must not open the picker [$diag]", 0, modelTaps)
        }
        check(leftAfter < leftBefore) { "model text did not move after horizontal swipe [$diag]" }
        composeTestRule.onNodeWithTag("reasoning_chip").assertIsDisplayed()
    }

    @Test
    fun shortModelName_wideComposerKeepsPillCompact() {
        setComposer(composerWidth = 420.dp)

        val toolbarBounds =
            composeTestRule
                .onNodeWithTag("composer_toolbar", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
        val toolbarWidth = toolbarBounds.right - toolbarBounds.left
        val modelChip = composeTestRule.onNodeWithTag("model_chip").getUnclippedBoundsInRoot()
        val reasoningChip = composeTestRule.onNodeWithTag("reasoning_chip").getUnclippedBoundsInRoot()
        val actionButton = composeTestRule.onNodeWithTag("mic_button").getUnclippedBoundsInRoot()
        val pillWidth = maxOf(modelChip.right, reasoningChip.right) - minOf(modelChip.left, reasoningChip.left)

        check(pillWidth < toolbarWidth * 0.8f) {
            "short model pill should remain content-sized, width=$pillWidth toolbar=$toolbarWidth"
        }
        check(actionButton.right > toolbarBounds.right - 32.dp) {
            "trailing action should stay near the toolbar edge, " +
                "right=${actionButton.right} toolbarRight=${toolbarBounds.right}"
        }
    }

    @Test
    fun changingModel_resetsScrolledLabelToTheStart() {
        val firstModel = "openrouter/some-extremely-long-model-name-preview-2026-with-extra-characters"
        val secondModel = "anthropic/another-extremely-long-model-name-preview-2026-with-extra-characters"
        // Pill shows the leaf only (batch-5B B1), so assertions track the leafs.
        val firstLeaf = firstModel.substringAfterLast('/')
        val secondLeaf = secondModel.substringAfterLast('/')
        // 420.dp: same geometry contract as the sibling swipe test — the mic is
        // fully offscreen here (deliberate overhang; its visibility is covered
        // by other tests), so it is no longer asserted.
        setComposer(model = firstModel, modelAfterTap = secondModel, composerWidth = 420.dp)

        val firstText = composeTestRule.onNodeWithText(firstLeaf)
        val leftBefore = firstText.getUnclippedBoundsInRoot().left
        val chipInteraction = composeTestRule.onNodeWithTag("model_chip")
        chipInteraction.performTouchInput { swipeLeft() }
        val chipNode = chipInteraction.fetchSemanticsNode()
        val scrollAxis = chipNode.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        val diag =
            "taps=$modelTaps chipSize=${chipNode.size} scrollAxis=$scrollAxis geo=${toolbarGeometry()}"
        composeTestRule.runOnIdle {
            assertEquals("the swipe must scroll, not open the picker [$diag]", 0, modelTaps)
        }
        val firstLeft = firstText.getUnclippedBoundsInRoot().left
        check(firstLeft < leftBefore) {
            "first model text did not move (leftBefore=$leftBefore firstLeft=$firstLeft $diag)"
        }

        composeTestRule.onNodeWithTag("model_chip").performClick()
        val secondText = composeTestRule.onNodeWithText(secondLeaf)
        val leftAfterModelChange = secondText.getUnclippedBoundsInRoot().left

        check(leftAfterModelChange >= leftBefore - 1.dp) {
            "new model label should reset to its initial left position"
        }
        composeTestRule.onNodeWithTag("reasoning_chip").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals("model selection should still invoke the picker", 1, modelTaps)
        }
    }

    @Test
    fun attachmentButton_togglesHorizontalTray() {
        setComposer()

        openAttachmentTray()
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photos").assertIsDisplayed()
        composeTestRule.onNodeWithText("File").assertIsDisplayed()

        closeAttachmentTray()
    }

    @Test
    fun attachmentTray_shrinksWithinConstrainedWidth() {
        setComposer(composerWidth = 180.dp)

        openAttachmentTray()
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photos").assertIsDisplayed()
        composeTestRule.onNodeWithText("File").assertIsDisplayed()
    }

    @Test
    fun attachmentTray_rtlKeepsActionsAccessible() {
        setComposer(layoutDirection = LayoutDirection.Rtl)

        openAttachmentTray()
        composeTestRule.onNodeWithText("Camera").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photos").assertIsDisplayed()
        composeTestRule.onNodeWithText("File").assertIsDisplayed()
    }

    @Test
    fun attachmentTray_actionsDispatchAndCloseTray() {
        setComposer()

        openAttachmentTray()
        composeTestRule.onNodeWithTag("attachment_action_camera").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.runOnIdle {
            assertEquals("Camera must dispatch its callback", 1, cameraTaps)
            assertEquals("Camera must not dispatch Photos", 0, photosTaps)
            assertEquals("Camera must not dispatch File", 0, fileTaps)
        }
        composeTestRule.onNodeWithTag("attachment_tray").assertDoesNotExist()

        openAttachmentTray()
        composeTestRule.onNodeWithTag("attachment_action_photos").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.runOnIdle { assertEquals("Photos must dispatch its callback", 1, photosTaps) }
        composeTestRule.onNodeWithTag("attachment_tray").assertDoesNotExist()

        openAttachmentTray()
        composeTestRule.onNodeWithTag("attachment_action_file").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.runOnIdle { assertEquals("File must dispatch its callback", 1, fileTaps) }
        composeTestRule.onNodeWithTag("attachment_tray").assertDoesNotExist()
    }

    @Test
    fun attachmentTray_dismissesOnActivityBack() {
        setComposer()

        openAttachmentTray()
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.mainClock.advanceTimeBy(300)

        composeTestRule.onNodeWithTag("attachment_tray").assertDoesNotExist()
    }

    @Test
    fun defaultComposer_showsOnlyModelNameWithoutProvider() {
        setComposer(model = "openai/gpt-5.5", composerWidth = 420.dp, showModelProvider = false)

        composeTestRule.onNodeWithText("gpt-5.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("openai/gpt-5.5").assertDoesNotExist()
    }

    @Test
    fun providerSetting_noLongerShownInPill() {
        // Batch-5B B1: the pill renders the leaf segment regardless of the
        // provider-display setting; "openai/gpt-5.5" stays visible only in the
        // model picker/manager.
        setComposer(model = "openai/gpt-5.5", composerWidth = 420.dp, showModelProvider = true)

        composeTestRule.onNodeWithText("gpt-5.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("openai/gpt-5.5").assertDoesNotExist()
    }

    @Test
    fun defaultComposer_showsOnlyFinalModelSegmentForNestedProvider() {
        setComposer(model = "openrouter/openai/gpt-5", composerWidth = 420.dp, showModelProvider = false)

        composeTestRule.onNodeWithText("gpt-5").assertIsDisplayed()
        composeTestRule.onNodeWithText("openai/gpt-5").assertDoesNotExist()
        composeTestRule.onNodeWithText("openrouter/openai/gpt-5").assertDoesNotExist()
    }
}
