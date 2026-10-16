# Detailed editing checklists

Use for a requested detailed audit or when a draft needs pattern-level diagnosis. These examples support editorial judgment.

## Step 2 — Audit against the checklist

Scan the draft and flag every hit. **Quote the offending text** so the user sees exactly what tripped. Group findings by category.

The lists below are prompts to look, not a find-and-replace. The standard is the outcome: no machine-default construction survives as the writer's own prose, and nothing gets removed just for matching a list.

### Lexical

Overused words: delve, leverage, utilize, facilitate, empower, foster, garner, underscore, showcase, highlight, emphasize, enhance, elevate, embark, harness, unlock, navigate, boast, supercharge, streamline, robust, crucial, pivotal, vital, paramount, comprehensive, seamless, vibrant, meticulous, intricate, multifaceted, profound, transformative, cutting-edge, ever-evolving, landscape, tapestry, realm, testament, beacon, journey, synergy, ecosystem, paradigm, cornerstone, game changer.

Flag when reached for by default instead of the plain word. "Crucial" is fine when something is actually crucial.

**Empty adverbs:** just, literally, honestly, simply, actually, truly, fundamentally, importantly, crucially, inherently, inevitably. Cut when they add nothing. **Keep them** when they carry emphasis, contrast, real uncertainty, or the writer's spoken rhythm. This one is a common over-correction. The same goes for a one-word sentence, a mild oath, or an aside the writer clearly meant: those are theirs.

### Phrase / cliché

"stands as a testament to", "plays a vital role", "in today's fast-paced world", "in the ever-evolving landscape", "when it comes to", "at its core", "the reality is", "the truth is", "in order to", "going forward", "it's worth noting", "it's important to note", "let's dive in", "nestled in the heart of", "at the end of the day", "the bottom line", "the future is bright", "what are your thoughts?".

### Structural

Each of these needs a concrete fix, not just a flag.

- **Binary contrast** — "It's not X, it's Y." / "The question isn't X, it's Y." The most recognizable shape on this list. → State Y directly. *"The question isn't the model, it's the eval"* → *"The eval matters more than the model."*
- **Negative listing** — "Not a framework. Not a library. A compiler." → Just say the last one.
- **Throat-clearing opener** — "Here's the thing", "Let me be clear", "I'll be honest", "The uncomfortable truth is". → Cut it and state the point.
- **Faux-insight setup** — "What nobody tells you", "the part everyone misses", "what most people get wrong". Flatters the writer as the lone expert. → Cut the setup, let the claim stand. *"The part everyone misses: distribution is the real moat"* → *"Distribution is the moat."*
- **Colon reveal** — noun phrase, colon, lowercase dramatic reveal. *"The detail that makes it work: a separate agent grades it."* → Rewrite as a plain sentence. Colons are for lists, labels, and quotes, not drama.
- **Fake-strong verb** — "serves as a centralized hub for", "acts as a bridge between". → Prefer "is" and "has", or a real verb. *"The app serves as a centralized hub for sponsor management"* → *"The app tracks sponsors, drafts, and due dates in one place."*
- **Superficial analysis** — trailing `-ing` clause that pretends to explain significance: "…, highlighting the team's commitment", "…, underscoring its impact", "…, creating a sense of community". → Cut it, or replace with the actual consequence. *"adds file search, highlighting a commitment to workflows"* → *"adds file search, so you can find old drafts without leaving the editor."*
- **Importance puffery** — "marks a pivotal moment", "solidifies its position", "plays a vital role". → State the fact, let the reader judge. *"The launch marks a pivotal moment for the company"* → *"The launch is the company's first paid product."*
- **Weasel attribution** — "experts agree", "studies show", "industry reports suggest", "widely regarded as". → Name the source or cut the claim. If the user has no source, ask; never invent one. **If you cut one, it still goes on the unverified list** — a removed claim is not a resolved claim.
- **Synonym cycling** — rotating terms for the same thing to avoid repetition. *"The agent reviews the draft. The assistant scores the piece. The tool suggests fixes."* → *"The agent reviews the draft, scores it, and suggests fixes."* If the clear word is right, repeat it.
- **Rhetorical setup** — "What if I told you", "Think about it:", "Plot twist:", self-answered "Question? Answer." pairs. → Drop it, make the point.
- **Fake-profound kicker** — the closing "deep" line that turns the point into a metaphor or mic-drop. → **Delete it. Do not rewrite it into a better metaphor and do not preserve its rhythm.** End on the clearest concrete sentence already in the draft, or add a plain takeaway.
- **Summary-recap ending** — "In conclusion", "Ultimately", "Overall", or a final paragraph restating the piece. The reader was just there. → Cut it.
- **Compulsive rule-of-three** — three adjectives, three clauses, three bullets, everywhere.
- **Robotic rhythm** — repeated sentence shapes, uniform paragraph length, topic→support→restate in every paragraph.
- **Dramatic fragmentation as a tic** — stacked punchy fragments used for rhythm rather than meaning: "That's it. That's the whole thing." See the burstiness guidance in [the editing workflow](../SKILL.md) before flagging this.
- **Listicle bloat** — every bullet a **Bold label:** then a sentence, where prose would read better.
- **Fake both-sides balance** — surveys both sides, commits to neither, when the writer clearly has a view.
- **False range** — "from X to Y" where X and Y are only unrelated examples. → Name the items directly.
- **Formulaic challenges section** — a generic "challenges and outlook" passage that adds no sourced fact. → Keep the concrete problem or plan; cut the stock frame.
- **Chatbot wrapper** — greetings, praise, process narration, and offers to continue that leaked into the deliverable. → Remove the wrapper and keep the content.
- **Knowledge-limit padding** — "based on available information", "details are scarce", or a cutoff disclaimer followed by a guess. → State only what the supplied material supports. Never infer a biography, motive, or date from missing evidence.
- **Unraised objection** — "I'm not saying...", "you might think...", or a fake alternative that the draft dismisses and never uses. → State the real claim or constraint. Keep a named objection that the piece answers in full.
- **Heading echo** — a heading followed by a first sentence that repeats it. → Start with the first new fact.
- **Diff-anchored prose** — documentation that describes the old draft or implementation when the genre calls for current behavior. → Describe the current state. Keep history in release notes, migration guides, or change logs.

**Never fix one of these with another one.** Replacing a binary contrast with a colon reveal, or a puffed-up claim with a differently puffed-up claim, is not a fix.

### Punctuation / formatting

Em dashes (see rule 4 in [the entrypoint](../SKILL.md)); boldface scattered mid-sentence to mark words that don't need marking; Title Case Headings; emoji used as headings or as a bullet system; headers over two-sentence sections; mechanically perfect parallel lists.

The emoji rule holds in social copy too. A caption can carry an emoji the way a person types one; a tidy emoji-per-line spec list is the machine default wearing a costume.

### Tone

Relentless positivity; automatic praise or agreement; no real opinion where the writer clearly has one; stacked qualifiers; over-explaining; marketing voice ("elevate your…", "unlock the power of"); abstraction where a concrete number or name belongs; engagement-bait closers.

Read `references/ai-slop-tells.md` when a pattern is unclear, several patterns overlap, or the user asks for an explanation. Do not load it for a routine clean edit.

## Step 3 — The Ogilvy pass

Step 2 catches what makes writing sound machine-made. This step catches what makes it *bad*, which is a different problem and the one that survives after the first is gone. Apply relevant checks to the draft.

The five checks below are David Ogilvy's rules from his 1982 "How to Write" memo, adapted into things you can actually inspect in a text. See [ogilvy-method.md](ogilvy-method.md) for the full method and the four rules that belong to the writer, not to you.

**1. Does it sound like a person talking?** Read the draft aloud in your head. Anywhere you stumble, trip over a clause, or run out of breath is a sentence to fix. Ogilvy's point is that people already have a voice, the one they speak in, and writing goes wrong when they reach for a more formal one. This is also the strongest argument for preserve mode: the writer's spoken rhythm is the asset, not the problem.

**2. Short words, short sentences, short paragraphs.** Flag long Latinate words where a plain one exists (utilize/use, commence/start, sufficient/enough, additional/more, terminate/end, purchase/buy, demonstrate/show). Flag sentences that run past roughly 30 words without earning it, and paragraphs over roughly five lines. Ogilvy's test is whether it survives being read aloud, not a word count, so treat these as triggers to look, not as limits to enforce.

**3. No jargon, because jargon is a thinking problem.** Distinct from the word list in Step 2, and often missed. Flag:
   - **Invented abstractions**, usually `-ize`, `-ization`, `-ality`, or an adverb built off a nominalization: reconceptualize, operationalize, socialize (a document), ideate, attitudinally, directionally, incentivize, functionality, actionability.
   - **Corporate filler**: circle back, bandwidth, low-hanging fruit, move the needle, boil the ocean, double-click on, level-set, north star.
   - **Unexplained domain terms** the stated audience wouldn't know. If there's no audience given and it changes the answer, ask who it's for.

   Ogilvy's charge against jargon is that it hides a lack of understanding. Test each flagged term: can the writer say it plainly? **If you cannot restate it plainly yourself, do not substitute a softer abstraction.** Turning "value orchestration across the enterprise data fabric" into "seamless data coordination across your organisation" swaps one opaque phrase for another and calls it tightened. That is laundering, and it is worse than leaving it, because it hides the hole. Say the claim is unresolved. See Step 3.5.

   **Keep exact technical language where precision depends on it.** Preserve code identifiers, commands, paths, types, error text, product names, and terms of art. In reader-facing prose, use a plain equivalent when it stays accurate. If no short equivalent exists, define the term once in parentheses. Do not add a lecture after the definition.

**4. Check the quotations and the facts.** Treat every quotation, statistic, date, name, attribution, and unit-less or baseline-less number as material you cannot verify, and say so plainly. Never invent, complete, or "improve" a quote. If a quote appears without a source, flag it. If a number appears without a unit or baseline, flag it.

   **The list is for load-bearing material** — the things the piece's truth rests on: an unsourced statistic, an unnamed authority, an anonymous testimonial, a truncated quotation, a number with no baseline, a claim about someone else's product or platform. It is not an inventory of every proper noun. A colleague's name, a street, or next Thursday in an internal email is the writer's own first-hand knowledge, and enumerating those to look thorough is its own failure. If nothing in the draft is load-bearing, say that in one line and move on.

   **If you believe a factual claim in the draft is wrong,** do not correct the value inside the deliverable — that puts a claim into the text that you did not verify. Flag it, name any inconsistency the draft itself exposes, and if you want to tell the user which way your doubt points, do it in conversation and mark it plainly as recollection rather than verification.

**5. Is the ask crystal clear?** After reading, could the recipient state exactly what they're meant to do next? This is the check most drafts fail and the one nobody runs.
   - **Email / message:** a missing or buried ask is critical. The action should be findable in one pass, not extracted from paragraph four.
   - **Social:** what should the reader think or do? One idea, one implied action.
   - **Longform:** what should the reader take away? Name it; if the draft doesn't have one, say so rather than inventing one.

   If the piece needs someone to *act* and the relationship allows a direct conversation, say so: Ogilvy's last rule is that writing is a poor substitute for going and asking. Recommending "don't send this, go talk to them" is a valid output of this skill.

### Step 3.5 — When the claim itself is missing

Sometimes the problem is upstream of the sentences. You cannot tell what the thing does, who the copy is for, or what is actually being claimed, and the answer changes materially depending on the answer. Polishing here is the worst available outcome: it hands back something that reads better and still says nothing, and it hides the real problem under a nicer surface.

When you hit this, do all four:

1. **Name it.** Say plainly that the draft makes no checkable claim, and quote the specific phrases that are carrying no content.
2. **Ask, once, for real.** What does it do, who is it for, where does it appear — whichever of these actually changes your answer. One or two questions, not an interview.
3. **Do not stop at the question.** A bare question is not a result. Give what you honestly can: the assessment, the cuts that are safe regardless (unmeasured puffery, an unfinished quotation), a conditional pass, or a skeleton with **empty** labelled slots showing which facts are missing.
4. **Do not fill the hole.** No invented product description, no supplied audience, no plausible example in a bracket.

The same shape applies when someone asks for copy and supplies no draft or no facts: ask for the real material, explain what you need and why, and don't produce a finished-looking page built out of guesses.

### On length

Ogilvy capped memos at two pages. Don't enforce that on a longform draft, but do apply the reasoning: if a piece can't be said in two pages, that usually means the thinking hasn't been simplified yet, not that it needs more room. Flag bloat, name what could be cut, and leave the call to the writer.

