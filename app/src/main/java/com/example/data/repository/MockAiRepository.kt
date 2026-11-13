package com.example.data.repository

import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockAiRepository : AiRepository {

    override val isCloudConnected: Boolean = false
    override val statusMessage: String = "Local Mode — Live Gemini cloud models require Firebase setup."

    override fun generateResponseStream(
        prompt: String,
        conversationHistory: List<MessageEntity>,
        persona: String,
        imageUri: String?
    ): Flow<String> = flow {
        val fullResponse = buildSmartResponse(prompt, persona, imageUri)

        // Split into words / chunks and emit with realistic streaming pacing
        val tokens = fullResponse.split(Regex("(?<=\\s|\\n)"))
        for (token in tokens) {
            emit(token)
            delay(28L) // 28ms between tokens simulates real LLM streaming TTFT & throughput
        }
    }

    private fun buildSmartResponse(
        prompt: String,
        persona: String,
        imageUri: String?
    ): String {
        val lower = prompt.lowercase()
        val imageNote = if (imageUri != null) {
            "📷 *Attached image detected and processed in local preview mode.*\n\n"
        } else ""

        val responseContent = when {
            lower.contains("code") || lower.contains("kotlin") || lower.contains("function") || lower.contains("compose") || lower.contains("android") || lower.contains("programming") || lower.contains("python") -> {
                """
### 💻 Solution & Code Implementation

Here is an efficient, clean implementation tailored for modern Kotlin & Jetpack Compose:

```kotlin
// Modern Jetpack Compose State Management
@Composable
fun SmartStateCounter(
    modifier: Modifier = Modifier,
    initialCount: Int = 0
) {
    var count by rememberSaveable { mutableIntStateOf(initialCount) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalIconButton(onClick = { if (count > 0) count-- }) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease")
            }
            Text(
                text = "Value: ${'$'}count",
                style = MaterialTheme.typography.titleMedium
            )
            FilledIconButton(onClick = { count++ }) {
                Icon(Icons.Default.Add, contentDescription = "Increase")
            }
        }
    }
}
```

#### Key Highlights:
1. **Recomposition Safety:** Uses `rememberSaveable` to retain state across process recreation.
2. **Material 3 Integration:** Adapts dynamically to system colors and rounded surfaces.
3. **Accessibility:** Touch targets follow the 48dp minimum standard.
                """.trimIndent()
            }

            lower.contains("email") || lower.contains("draft") || lower.contains("letter") -> {
                """
### ✉️ Drafted Email Template

**Subject:** Update on Next Steps & Project Collaboration

Hi team,

I hope you're having a productive week. 

I wanted to touch base regarding our ongoing project timeline. We've made substantial headway on the core architecture and are now focusing on finalizing the user experience and testing phases.

**Key Upcoming Milestones:**
- **Design Review:** Finalizing responsive components and theme polish.
- **Integration Testing:** Ensuring edge-to-edge stability and smooth state flows.
- **Target Delivery:** On schedule for review by the end of the sprint.

Please let me know if you have questions or specific adjustments before our sync tomorrow.

Best regards,  
*Dinthi AI Assistant*
                """.trimIndent()
            }

            lower.contains("startup") || lower.contains("idea") || lower.contains("brainstorm") || lower.contains("business") -> {
                """
### 💡 Creative AI & Tech Startup Concepts

Here are 4 high-potential project ideas with strong product-market fit:

1. **SmartMicro Habit Architect**
   - **Concept:** Micro-coaching app that adapts daily routines dynamically based on sleep, calendar availability, and productivity momentum.
   - **Monetization:** Freemium with pro analytics and personalized audio coaching.

2. **DocuLens AI for Engineers**
   - **Concept:** Real-time architectural diagram generator that parses codebases and creates interactive flowcharts automatically.
   - **Edge:** Cuts developer onboarding time in half.

3. **EcoTrack Supply Verifier**
   - **Concept:** Lightweight compliance scanner verifying supply chain sustainability metrics from invoices and packing slips.
   - **Edge:** High corporate ESG compliance demand.

4. **OmniVoice Audio Scribe**
   - **Concept:** Meeting summarizer with speaker diarization and instant action-item delegation into issue trackers.
                """.trimIndent()
            }

            lower.contains("quantum") || lower.contains("explain") || lower.contains("how does") || lower.contains("science") -> {
                """
### 🔬 Understanding Complex Systems: A Clear Breakdown

To understand this concept intuitively, let's break it down into core principles:

- **The Core Problem:** Classical systems operate strictly with binary states (like light switches that are strictly ON or OFF).
- **The Modern Analogy:** Imagine a spinning coin on a table. While it spins, it represents both heads and tails simultaneously with varying probabilities until it settles down.
- **Superposition:** The mathematical ability to exist in multiple configurations simultaneously.
- **Entanglement:** Two particles linked such that measuring one instantly determines the other, regardless of distance.

#### Why This Matters:
This allows systems to evaluate millions of potential solutions concurrently rather than sequentially, unlocking exponential speedups for cryptography, material discovery, and logistics optimization.
                """.trimIndent()
            }

            lower.contains("workout") || lower.contains("fitness") || lower.contains("plan") || lower.contains("health") -> {
                """
### 🏋️ Tailored Daily Fitness & Wellness Plan

Here is a balanced 3-day split routine focused on functional strength and recovery:

- **Day 1: Upper Body Push & Core**
  - Push-ups / Dumbbell Press: 3 sets of 10-12 reps
  - Overhead Shoulder Press: 3 sets of 10 reps
  - Plank Hold: 3 sets of 45 seconds
  - Cool-down stretch: 5 minutes

- **Day 2: Lower Body & Mobility**
  - Goblet Squats: 4 sets of 10 reps
  - Romanian Deadlifts: 3 sets of 12 reps
  - Walking Lunges: 3 sets of 10 reps per leg
  - Dynamic hip openers: 5 minutes

- **Day 3: Active Recovery & Cardio**
  - 30-minute brisk walk or zone-2 cycling
  - Full-body mobility routine and hydration focus
                """.trimIndent()
            }

            else -> {
                val personaStyle = when (persona) {
                    "Creative" -> "Expressive and visionary perspective:"
                    "Concise" -> "Direct summary:"
                    "Coder" -> "Technical overview:"
                    else -> "Here is what you need to know:"
                }

                """
### Dinthi AI Response

$personaStyle

Regarding your question **"$prompt"**:

Dinthi AI is designed to assist you with reasoning, brainstorming, coding, and day-to-day productivity. 

Here are key points to consider:
- **Clarity & Context:** Providing specific details helps refine suggestions and outputs.
- **Multimodal Flexibility:** You can attach images, speak with voice dictation, and have responses read aloud via Text-to-Speech.
- **Local Control:** Your conversation history is stored securely on your device with full privacy.

Feel free to ask follow-up questions or request code, summaries, or alternate variations!
                """.trimIndent()
            }
        }

        return imageNote + responseContent
    }
}
