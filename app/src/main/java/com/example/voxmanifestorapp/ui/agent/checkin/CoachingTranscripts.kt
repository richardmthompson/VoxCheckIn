package com.example.voxmanifestorapp.ui.agent.checkin

/** Data class representing a coaching example with metadata */
data class CoachingExample(val title: String, val strategies: List<Strategy>, val content: String)

/**
 * Object containing all coaching conversation examples organized by strategy type. These examples
 * are used by the DialogueChain system to provide context and training patterns for the AI to
 * generate appropriate coaching responses.
 */
object CoachingTranscripts {

  // === CONVERSATION STARTING EXAMPLES ===

  /*
  // ORIGINAL EXAMPLES - COMMENTED OUT FOR REFERENCE
  // These were too formal and assumed continuity ("since we last spoke")
  val CONVERSATION_STARTING_EXAMPLES_ORIGINAL = listOf(
      CoachingExample(
          title = "Opening with General Well-Being",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: How have things been for you recently?
          Client: It's been okay. Work has been busy, but I'm managing.
          Coach: Sounds like you've had a lot on your plate at work. What does "managing" look like for you right now?
          """.trimIndent()
      ),
      CoachingExample(
          title = "Opening with Focus on Aspirations",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: What's been present for you lately regarding your aspirations or intentions?
          Client: I've been thinking a lot about changing careers lately. I'm not sure if it's just a phase or something I should take seriously.
          Coach: That's an interesting thought. What's prompting these considerations about a career change?
          """.trimIndent()
      ),
      CoachingExample(
          title = "Opening with Wish Progress",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: How are you feeling about your progress with your wishes since we last spoke?
          Client: I haven't made as much progress as I'd hoped. I got distracted by some family issues that came up.
          Coach: Life has a way of throwing unexpected things into our path. What kind of family matters came up that needed your attention?
          """.trimIndent()
      ),
      CoachingExample(
          title = "Opening with Recent Thoughts",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: What's been on your mind since we last connected, particularly around the things you're wanting to create or achieve?
          Client: I've been wondering if my goals are too ambitious. Maybe I'm setting myself up for failure by aiming too high.
          Coach: That's a common reflection. What's bringing up these questions about the ambition level of your goals?
          """.trimIndent()
      ),
      CoachingExample(
          title = "Opening with Positive Focus",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: Welcome back! What's one thing that's gone well for you since we last spoke?
          Client: I finally had that difficult conversation with my colleague that I was avoiding, and it went much better than I expected.
          Coach: That takes courage, and it's great it went well. What do you think helped make the conversation go better than expected?
          """.trimIndent()
      ),
      CoachingExample(
          title = "Opening with Energy Check",
          strategies = listOf(Strategy.CONVERSATION_STARTING),
          content = """
          Coach: I'm glad to see you today. What's your energy level like coming into our conversation?
          Client: Pretty low, to be honest. I didn't sleep well last night, and I've been feeling a bit down generally.
          Coach: I appreciate you sharing that. It can be hard to show up when your energy is low. What's been affecting your sleep and mood, if you're open to sharing?
          """.trimIndent()
      )
  )
  */

  val CONVERSATION_STARTING_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Casual Mind Check",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hey there! Welcome back. Let's check in. What's on your mind today?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Recent Activity Check",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hi! Good to see you again. What have you been up to recently?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Open Topic Invitation",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hello! Welcome back to your check-in. What would you like to talk about today?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Current State Check",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hey there! Let's check-in!  What recent progress have you made toward your goals?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Direct Present Focus",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hey! Good to connect with you again. What's happening in your world?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Goals Reflection Check",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hi! Welcome back. What's been on your mind lately regarding your goals?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Aspirations Focus",
                          strategies = listOf(Strategy.CONVERSATION_STARTING),
                          content =
                                  """
            Hey there! Good to see you. What intentions have been on your mind recently?
            """.trimIndent()
                  )
          )

  // === RAPPORT BUILDING EXAMPLES ===
  val RAPPORT_BUILDING_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Building Initial Connection",
                          strategies = listOf(Strategy.RAPPORT_BUILDING),
                          content =
                                  """
            Client: This is my first time doing something like this. I'm not really sure what to expect.
            Coach: That's completely understandable. Many people feel a bit uncertain when they start this process. My role is to support your journey and provide a space where you can explore what matters to you. What made you decide to give this a try?
            Client: I've been feeling stuck in several areas of my life, and nothing I've tried on my own seems to be working.
            Coach: It sounds like you're seeking a new way forward because your own approaches haven't broken through that feeling of being stuck. Reaching out is a positive step.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Creating Safety for Vulnerability",
                          strategies =
                                  listOf(Strategy.RAPPORT_BUILDING, Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I'm usually pretty private about my problems. I tend to just handle things on my own.
            Coach: I appreciate you sharing that with me. It takes a different kind of strength to open up when you're used to handling things independently. There's no pressure to share more than feels comfortable for you.
            Client: I guess I need to know that I won't be judged.
            Coach: That makes perfect sense. This is a judgment-free space, focused on understanding your perspective.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Finding Common Ground",
                          strategies = listOf(Strategy.RAPPORT_BUILDING),
                          content =
                                  """
            Client: I always struggle with work-life balance. It seems impossible to do well at work and still have energy for my personal life.
            Coach: That's a challenge many people face; you're certainly not alone in that. Finding that balance is less like solving a problem once and for all, and more like an ongoing navigation.
            Client: Exactly. It feels like a constant juggle.
            Coach: What does a better balance look like in your ideal scenario?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Acknowledging Client's Effort to Engage",
                          strategies = listOf(Strategy.RAPPORT_BUILDING, Strategy.AFFIRM_SUPPORT),
                          content =
                                  """
            Client: I almost cancelled today because I'm feeling so overwhelmed with everything.
            Coach: I really appreciate you making the effort to be here, especially when you're feeling overwhelmed. Sometimes just showing up is the hardest part. What's contributing most to that feeling of overwhelm right now?
            Client: It's mainly a huge project at work with a tight deadline.
            Coach: Big projects with tight deadlines can definitely be all-consuming.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Normalizing Feelings of Uncertainty",
                          strategies =
                                  listOf(Strategy.RAPPORT_BUILDING, Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I'm not sure if I'm even explaining this right, or if it makes sense.
            Coach: It's very common to feel that way when you're trying to articulate something complex or personal. Please know that however you express it is perfectly fine. I'm here to listen and understand.
            Client: Okay, thank you. That helps.
            Coach: Take your time. What's on your mind?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Setting Collaborative Tone Early",
                          strategies = listOf(Strategy.RAPPORT_BUILDING),
                          content =
                                  """
            Client: So, are you going to tell me what to do about my situation?
            Coach: That's a great question. My approach is more about helping you explore your own insights and solutions. I'll ask questions to help you think things through, but the answers and decisions will come from you. How does that sound?
            Client: I guess that makes sense. I do want to figure it out for myself.
            Coach: Excellent. What's the situation you're hoping to get more clarity on today?
            """.trimIndent()
                  )
          )

  // === EXPERIENCE EXPLORATION EXAMPLES ===
  val EXPERIENCE_EXPLORATION_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Exploring a Recent Disagreement",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: I had an argument with my partner yesterday and it's still on my mind.
            Coach: I'm sorry to hear that. Can you tell me a bit about what happened during the argument?
            Client: It was about chores, again. I feel like I do everything.
            Coach: So the disagreement centered on household responsibilities, and you're feeling the burden of an unequal share. What specifically was said or done that's sticking with you?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring a Recent Success",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: I actually managed to finish that presentation on time, and it went really well!
            Coach: That's fantastic news! Congratulations. What do you think were the key factors that contributed to your success with it?
            Client: I started earlier than usual and asked a colleague for feedback, which really helped.
            Coach: Starting early and seeking feedback – those sound like effective strategies. What prompted you to try those this time?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring a Moment of Overwhelm",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: Yesterday at work, I just hit a wall. I felt completely overwhelmed.
            Coach: Can you describe what was happening leading up to that moment when you hit a wall?
            Client: I had three deadlines looming, and my boss added another urgent task to my plate.
            Coach: So, multiple deadlines plus an unexpected urgent task all at once. What did that overwhelm feel like for you?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring a Specific Interaction",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: I tried talking to my manager about my workload, like we discussed.
            Coach: I remember you were planning to do that. How did that conversation go?
            Client: Not great. He seemed dismissive and said everyone is busy.
            Coach: That sounds disappointing. When he seemed dismissive, what was his specific response or reaction?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring a Time-Bound Experience",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: I felt really good last week, very energetic and positive.
            Coach: That's wonderful. What was different about last week? What were you doing, or what was happening in your life then?
            Client: I was on vacation, completely unplugged from work.
            Coach: Being on vacation and unplugged clearly had a positive impact. What specifically about that experience contributed to you feeling so good?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring an Internal Experience (e.g., a feeling)",
                          strategies = listOf(Strategy.EXPERIENCE_EXPLORATION),
                          content =
                                  """
            Client: I've been feeling a lot of self-doubt lately.
            Coach: Can you give me an example of a recent situation where that self-doubt showed up strongly?
            Client: Yes, during a team meeting yesterday, I hesitated to share an idea because I thought it might sound stupid.
            Coach: So in that team meeting, self-doubt made you hesitate. What was the idea, and what were the specific thoughts telling you it might sound stupid?
            """.trimIndent()
                  )
          )

  // === REFLECTION DEEPENING EXAMPLES ===
  val REFLECTION_DEEPENING_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Exploring Values Behind Actions",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I always say yes to extra projects at work, even when I'm already swamped.
            Coach: What do you think drives that tendency to say yes, even when you're overloaded?
            Client: I guess I want to be seen as reliable and capable. And I worry about missing out on opportunities.
            Coach: So, being perceived as reliable and capable, and not missing opportunities, are really important to you. What's the impact of consistently prioritizing those things?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring the 'Why' Behind a Goal",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I really want to get promoted this year.
            Coach: What would achieving that promotion mean to you personally, beyond the title or pay rise?
            Client: It would mean I'm good at what I do, that my hard work is recognized.
            Coach: So, that recognition and validation of your skills and effort are significant drivers for you. What does 'being good at what you do' allow for you?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring a Recurring Pattern",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I notice I tend to avoid conflict, even when it means my needs aren't met.
            Coach: That's a significant awareness. When you choose to avoid conflict, what are you hoping to achieve or prevent in that moment?
            Client: I guess I just want things to be peaceful. I don't like tension.
            Coach: So, maintaining peace and avoiding tension are key priorities. What's the long-term cost or benefit of that pattern for you?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring Underlying Beliefs",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I feel like I have to be perfect in everything I do.
            Coach: That's a lot of pressure to carry. Where do you think that belief about needing to be perfect comes from?
            Client: I'm not sure... maybe from my upbringing? My parents had very high expectations.
            Coach: If you were to imagine, just for a moment, letting go of that need for perfection, what might become possible?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring Future Self Aspirations",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I want to be more confident.
            Coach: If you were that more confident version of yourself, how would you be thinking, feeling, and acting differently in your daily life?
            Client: I'd speak up more in meetings, I wouldn't second-guess myself so much, and I'd take on new challenges without so much fear.
            Coach: That's a clear picture. What's one small aspect of that confident self you could experiment with bringing into your life this week?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Exploring What Truly Matters",
                          strategies = listOf(Strategy.REFLECTION_DEEPENING),
                          content =
                                  """
            Client: I'm so busy all the time, but I often feel like I'm not achieving anything meaningful.
            Coach: When you think about what 'meaningful achievement' truly looks like for you, what comes to mind?
            Client: It's less about ticking off tasks and more about making a positive impact, or learning and growing.
            Coach: Making a positive impact, learning, and growing – those are powerful. How much of your current busyness aligns with those deeper values?
            """.trimIndent()
                  )
          )

  // === REFLECTIVE MIRRORING EXAMPLES ===
  val REFLECTIVE_MIRRORING_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Mirroring Numbness and Disconnection",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: I'm not sure how I'm feeling today. I just got through work and I'm here, but I feel kind of numb or neutral. Like I was on autopilot.
            Coach: So you're feeling a bit numb, perhaps disconnected, just going through the motions today.
            Client: Yes, exactly. Like I wasn't really engaged.
            Coach: Your body was there, but your mind and engagement were elsewhere.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Mirroring Feeling Behind and Overwhelmed",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: There's just **too much** to do, and I constantly feel like I'm behind on everything. It's exhausting.
            Coach: It sounds like you're carrying a heavy load, feeling overwhelmed by the volume of tasks and the persistent sense of being behind.
            Client: That's it. I can't seem to catch up.
            Coach: That constant feeling of not catching up sounds incredibly draining.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Mirroring Frustration and Self-Disappointment",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: I planned to work on my personal project this weekend, but I procrastinated the whole time. I'm really disappointed in myself.
            Coach: So you had a plan for your project, but procrastination took over, and now you're left feeling disappointed with yourself.
            Client: Yes, I feel like I wasted the whole weekend.
            Coach: That sense of a wasted weekend and the accompanying self-disappointment is strong.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Mirroring Anxiety and Negative Anticipation",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: I have a networking event tomorrow and I'm really anxious. I keep imagining saying something stupid or standing alone.
            Coach: It sounds like you're experiencing significant anxiety about this event, with your mind replaying concerns about awkwardness or isolation.
            Client: Totally. The thought of it makes my stomach churn.
            Coach: That physical reaction, the stomach churning, highlights how real this anxiety feels for you.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Mirroring Discouragement from a Setback",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: I've been trying to meditate every morning. It started well, but I missed three days this week. Now I feel discouraged.
            Coach: You were on a good track with your meditation, then missed a few days, and that's left you feeling discouraged.
            Client: Yeah, I feel like I've failed.
            Coach: So that feeling of discouragement is tied to a sense of having failed to maintain the habit.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Mirroring Feeling Stuck and Lacking Excitement",
                          strategies = listOf(Strategy.REFLECTIVE_MIRRORING),
                          content =
                                  """
            Client: Lately I just feel stuck. I'm not excited about anything. Days blur together. I do the bare minimum.
            Coach: It sounds like you're describing a feeling of being stuck, where life lacks excitement and days feel monotonous, leading you to just do the minimum.
            Client: That sums it up. It's like I'm on a treadmill.
            Coach: That image of being on a treadmill, expending energy but not really going anywhere new or exciting, really captures that stuck feeling.
            """.trimIndent()
                  )
          )

  // === EMOTIONAL VALIDATION EXAMPLES ===
  val EMOTIONAL_VALIDATION_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Validating Overwhelm from Work Stress",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I feel so overwhelmed with my job. There's just **too much** to do, and I constantly feel like I'm behind on everything.
            Coach: That sounds absolutely exhausting. It's completely understandable to feel overwhelmed when you're facing a mountain of tasks and a constant sense of being behind.
            Client: It really is.
            Coach: What's the heaviest piece on your plate right now?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Validating Disappointment from a Setback",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I just found out I didn't get the promotion I wanted. I'm trying to stay positive, but honestly I'm pretty upset and discouraged.
            Coach: It's totally valid to feel upset and discouraged after not getting a promotion you worked hard for. Those feelings are natural.
            Client: Thanks. It just really stings.
            Coach: Of course it stings. You invested yourself in it.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Validating Frustration with Procrastination",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I planned to work on my personal project this weekend, but I procrastinated the whole time. I'm really disappointed in myself.
            Coach: It sounds incredibly frustrating when you have clear intentions and then find yourself unable to start, leading to that disappointment. That's a tough cycle.
            Client: It is. I just don't know why I do it.
            Coach: Many people struggle with that. What made it hard to start this weekend?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Validating Social Anxiety",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I have a networking event tomorrow and I'm really anxious. I keep imagining saying something stupid or standing alone in a corner.
            Coach: It's completely understandable to feel anxious before a networking event, especially when your mind is painting those kinds of worrying pictures. Many people find such situations challenging.
            Client: It's just, I hate small talk.
            Coach: That makes sense; if small talk feels inauthentic to you, it would naturally add to the discomfort.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Validating Regret After an Outburst",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: Yesterday I got really angry at my partner over something small. I exploded and yelled, then felt horrible. I don't want to react like that.
            Coach: You're upset with how you responded, and it's natural to feel horrible and regretful after an outburst like that, especially with someone you care about.
            Client: I really do. I feel so guilty.
            Coach: Those feelings of guilt show how much you care about your partner and how you interact.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Validating Feelings of Self-Doubt",
                          strategies = listOf(Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: Every time I speak in a meeting, I later second-guess everything I said. I feel like I sound stupid.
            Coach: That sounds incredibly draining. It's really tough when your inner critic is so loud and makes you doubt yourself like that, especially after you've put yourself out there.
            Client: It is. I just wish I could stop it.
            Coach: That constant second-guessing can be exhausting.
            """.trimIndent()
                  )
          )

  // === PERSPECTIVE SHIFTING EXAMPLES ===
  val PERSPECTIVE_SHIFTING_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Shifting from Numbness to Engagement",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: I felt a bit disconnected at times, like I was on autopilot. Nothing really happened, good or bad.
            Coach: So work felt flat, and you were on autopilot. What kind of days, or moments within days, *do* feel engaging and connected for you?
            Client: When I'm problem-solving or learning something new.
            Coach: So, problem-solving and learning are keys to feeling engaged.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Shifting from Being Derailed to Finding Grounding",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: I tried making a to-do list... But then I got a last-minute request from my boss and it threw everything off. After that, I felt lost again.
            Coach: Your plan was working for a bit, until that curveball hit. When unexpected things derail your plans, what's one small thing that might help you feel a bit more grounded or re-focused?
            Client: Maybe taking a five-minute break to just breathe.
            Coach: A five-minute breathing break – that's a great idea.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Shifting from Perfectionism to Actionable Steps",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: I think it's because the project is big and I have this idea it needs to be perfect. That pressure makes me freeze up.
            Coach: That pressure for perfection can definitely be paralyzing. If you were to let go of 'perfect' for a moment, what's one small, imperfect step you could take just to get started?
            Client: Maybe just drafting a very rough outline.
            Coach: A rough outline – excellent. How does that feel compared to 'perfect'?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Shifting from Dread of Small Talk to Positive Outcome",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: I hate small talk. It feels so fake and forced. I won't know anyone.
            Coach: Starting conversations with strangers can be tough, especially if small talk feels inauthentic. Let's imagine for a moment that, despite the small talk, a conversation at this event went surprisingly well. What would that look or feel like for you?
            Client: I guess I'd connect with someone over a shared interest.
            Coach: Connecting over a shared interest – that sounds much more appealing.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Shifting from Feeling Stuck to Identifying Desires",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: Lately I just feel stuck. Work is boring. I don't have any hobbies anymore. I just work, watch TV, and sleep.
            Coach: Repeating that cycle with no spark can really wear you down. If you could wave a magic wand, what's one thing you wish your days had more of?
            Client: Excitement, or maybe just some joy.
            Coach: Excitement or joy. What used to bring you those feelings?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Shifting from Failure to Learning Opportunity",
                          strategies = listOf(Strategy.PERSPECTIVE_SHIFTING),
                          content =
                                  """
            Client: I didn't get the promotion. I feel like I failed.
            Coach: It's understandable to feel that way when something you wanted doesn't happen. Aside from the outcome, what did you learn from the process of going for that promotion?
            Client: I learned I need more experience in X and Y.
            Coach: That's valuable insight. So, this experience has highlighted areas for growth.
            """.trimIndent()
                  )
          )

  // === AFFIRM SUPPORT EXAMPLES ===
  val AFFIRM_SUPPORT_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Affirming a Client's Success",
                          strategies = listOf(Strategy.AFFIRM_SUPPORT),
                          content =
                                  """
            Client: I actually managed to stick to my healthy eating plan all week! I usually cave after a couple of days, but this time I did it.
            Coach: That's a fantastic achievement! Sticking to a new plan for a whole week when you've found it challenging before shows real commitment and strength. You should be proud.
            Client: Thanks, I am!
            Coach: What helped you succeed this week?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Acknowledging Client's Courage and Honesty",
                          strategies = listOf(Strategy.AFFIRM_SUPPORT, Strategy.RAPPORT_BUILDING),
                          content =
                                  """
            Client: Yesterday I got really angry at my partner... I exploded and yelled, then felt horrible. It's hard to admit that.
            Coach: It takes a lot of courage to acknowledge when you've reacted in a way you regret, and to share that openly. I really appreciate your honesty here.
            Client: Thank you. I just don't want to be that person.
            Coach: It's clear you're reflecting deeply on this.
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Expressing Compassion for Client's Struggle",
                          strategies =
                                  listOf(Strategy.AFFIRM_SUPPORT, Strategy.EMOTIONAL_VALIDATION),
                          content =
                                  """
            Client: I've been doubting myself a lot at work. Every time I speak in a meeting, I later second-guess everything I said. It's exhausting.
            Coach: That sounds incredibly tough to go through, carrying that constant self-doubt and the exhaustion that comes with it. You're navigating a really challenging internal experience.
            Client: It really is.
            Coach: What kind of support would feel helpful to you with this?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Affirming Client's Self-Awareness",
                          strategies = listOf(Strategy.AFFIRM_SUPPORT),
                          content =
                                  """
            Client: I realized that my fear of failure is what's really holding me back from starting my own business.
            Coach: That's a very powerful and insightful realization. Identifying the root fear like that shows a great deal of self-awareness.
            Client: It took me a while to get there.
            Coach: That journey to awareness is significant. What does this realization open up for you now?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Acknowledging Client's Resilience",
                          strategies = listOf(Strategy.AFFIRM_SUPPORT),
                          content =
                                  """
            Client: After I lost my job, I was devastated for a while, but then I started looking at it as an opportunity to retrain.
            Coach: To go from devastation to seeing an opportunity for retraining shows incredible resilience and a really resourceful mindset. That's a remarkable shift.
            Client: Thank you. It wasn't easy.
            Coach: I can only imagine. What helped you make that shift?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Affirming Client's Strengths (from Ex2 Transcript)",
                          strategies = listOf(Strategy.AFFIRM_SUPPORT),
                          content =
                                  """
            Client: I've realized that my technical background, being upfront with clients, and sharing accurate facts are my strengths. So, that is what I will focus on going forward.
            Coach: That's a fantastic realization! Recognizing and deciding to lean into your core strengths like your technical expertise, your upfront nature, and your commitment to facts is a really powerful approach.
            Client: It feels like a weight off my shoulders.
            Coach: How does focusing on these strengths change how you view your role or upcoming challenges?
            """.trimIndent()
                  )
          )

  // === CAUSAL INQUIRY EXAMPLES ===
  val CAUSAL_INQUIRY_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Exploring Sleep Issues",
                          strategies = listOf(Strategy.CAUSAL_INQUIRY),
                          content =
                                  """
            Client: I've been struggling with sleep lately. I'm constantly tired during the day.
            Coach: That sounds difficult. What do you think might be causing these sleep difficulties?
            Client: I'm not sure. Maybe stress? I've had a lot going on at work.
            Coach: Could there be a connection between your work stress and your sleep troubles? How does your mind feel when you're trying to fall asleep?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Investigating Procrastination Patterns",
                          strategies = listOf(Strategy.CAUSAL_INQUIRY),
                          content =
                                  """
            Client: I keep procrastinating on my important project, and then I feel terrible about it afterward.
            Coach: What do you notice happens right before you start procrastinating? Are there specific triggers?
            Client: I think I get overwhelmed looking at how much work it will be.
            Coach: I'm wondering if there might be a link between that feeling of overwhelm when facing a large project and your tendency to procrastinate. How does the size of the task affect your ability to start?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Examining Workplace Frustration",
                          strategies = listOf(Strategy.CAUSAL_INQUIRY),
                          content =
                                  """
            Client: I'm getting increasingly frustrated with my team at work. I feel like I'm doing all the heavy lifting.
            Coach: When did you first notice this frustration building? Was there a specific incident that started it?
            Client: It's been gradual, but there was a big project last month where I ended up doing most of the work.
            Coach: I'm curious about the connection between your team dynamics and this workload imbalance. What happens when you try to delegate or ask for support from team members?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Understanding Relationship Conflict",
                          strategies = listOf(Strategy.CAUSAL_INQUIRY),
                          content =
                                  """
            Client: My partner and I keep having the same argument over and over. It's exhausting.
            Coach: What typically happens right before these recurring arguments start?
            Client: Usually it's when we're both tired, often in the evening after work.
            Coach: Could there be a relationship between your energy levels and these conflicts? How do your conversations differ when you're both well-rested versus tired?
            """.trimIndent()
                  )
          )

  // === DESIRED STATE EXPANSION EXAMPLES ===
  val DESIRED_STATE_EXPANSION_EXAMPLES =
          listOf(
                  CoachingExample(
                          title = "Exploring Health Goals",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I think I need to get healthier. It's been on my mind lately.
            Coach: Could you tell me what being healthier means to you specifically?  What kind of things would that entail?
            Client: I guess eating better and maybe exercising more consistently.
            Coach: If you were living this healthier lifestyle, what might a typical day look like for you? What specific changes would be most meaningful?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Expanding on Career Aspirations",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I'd like to advance in my career. I feel like I've been stagnant for too long.
            Coach: What would career advancement look like for you? What position or role are you aspiring toward?
            Client: I'd like to move into management, maybe leading a small team.
            Coach: If you were in that management role, what aspects of it would be most fulfilling for you? How would you know you're successful in that position?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Clarifying Financial Goals",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I want to make more money. My current salary just isn't enough.
            Coach: What kind of income would feel sufficient to you?  What would having that additional money allow you to do?
            Client: I'd like to make at least 20% more, which would let me save for a house and travel occasionally.
            Coach: That's helpful specificity. What would it feel like to have that financial freedom? How might your day-to-day life be different?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Visualizing Work-Life Balance",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I need a better work-life balance. Right now, it's all work and no life.
            Coach: What would a balanced life look like for you? How would you distribute your time and energy differently?
            Client: I'd work reasonable hours, have evenings free, and actually use my weekends for fun things.
            Coach: If you had that balance, what specific activities or relationships would you prioritize in that newly available time?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Defining a Peaceful Life",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I just want a more peaceful life. Everything feels so chaotic right now.
            Coach: Can you speak a bit about how you would know, from the outside, that you had a peaceful life?
            Client: Less rushing, fewer commitments, and more quiet time to just be.
            Coach: That's a beautiful vision. If you were living this peaceful life, how might your surroundings change? What would your home environment feel like, for example?
            """.trimIndent()
                  ),
                  CoachingExample(
                          title = "Elaborating on Relationship Desires",
                          strategies = listOf(Strategy.DESIRED_STATE_EXPANSION),
                          content =
                                  """
            Client: I want to have deeper connections with people. I feel like my relationships are all surface-level.
            Coach: Can you talk a bit about what qualities you think these deeper connections could have?
            Client: More honesty, vulnerability, and regular meaningful conversations instead of just small talk.
            Coach: If you had these deeper connections, what kinds of conversations would you be having? How might you feel after spending time with these people?
            """.trimIndent()
                  )
          )

  /**
   * Returns coaching examples that match the specified strategy. Used by the DialogueChain system
   * to provide relevant examples for LLM prompts.
   */
  fun getExamplesForStrategy(strategy: Strategy): List<CoachingExample> {
    return when (strategy) {
      Strategy.CONVERSATION_STARTING -> CONVERSATION_STARTING_EXAMPLES
      Strategy.RAPPORT_BUILDING -> RAPPORT_BUILDING_EXAMPLES
      Strategy.EXPERIENCE_EXPLORATION -> EXPERIENCE_EXPLORATION_EXAMPLES
      Strategy.REFLECTION_DEEPENING -> REFLECTION_DEEPENING_EXAMPLES
      Strategy.REFLECTIVE_MIRRORING -> REFLECTIVE_MIRRORING_EXAMPLES
      Strategy.EMOTIONAL_VALIDATION -> EMOTIONAL_VALIDATION_EXAMPLES
      Strategy.PERSPECTIVE_SHIFTING -> PERSPECTIVE_SHIFTING_EXAMPLES
      Strategy.AFFIRM_SUPPORT -> AFFIRM_SUPPORT_EXAMPLES
      Strategy.CAUSAL_INQUIRY -> CAUSAL_INQUIRY_EXAMPLES
      Strategy.DESIRED_STATE_EXPANSION -> DESIRED_STATE_EXPANSION_EXAMPLES
    }
  }
}
