# Decisions Log
## SnowReach — Requirements Negotiation
**Date:** 2026-03-26

This document records key decisions made during requirements negotiation and the reasoning
behind each, for future reference by any team member or new conversation context.

---

## Authentication

| Decision | Rationale |
|----------|-----------|
| Both email/password AND social login (Google/Apple) | Maximises accessibility; social login reduces friction; email/password for users without Google/Apple accounts |
| Phone verification optional at registration, required before first Worker job | Balances signup friction with trust; phone is a meaningful identity signal for Workers who will visit homes |

---

## Location & Privacy

| Decision | Rationale |
|----------|-----------|
| Addresses entered manually (not GPS) | User preference; GPS is available in v2 native apps |
| Addresses kept private until escrow confirmed | Protects both parties from no-show/spam; address only relevant once job is real |
| Only approximate distance shown in search | Workers and Requesters need distance for relevance but not exact location until committed |

---

## User Roles

| Decision | Rationale |
|----------|-----------|
| One account can be both Worker and Requester | Real-world scenario: snowblower owner may also need help when equipment is down |

---

## Job Matching

| Decision | Rationale |
|----------|-----------|
| Sequential requests (one Worker at a time) | Prevents race conditions and awkward multi-accept scenarios; simpler UX |
| Client selects from list (not auto-assigned) | Gives Requester agency; aligns with TaskRabbit model which works well |
| 10-minute Worker acceptance window | Long enough to be practical; short enough to not keep Requester waiting |
| Worker sorted by rating then distance | Rating is the primary trust signal; distance is secondary for practicality |

---

## Payments & Escrow

| Decision | Rationale |
|----------|-----------|
| Full escrow model | Protects Requester (money not lost if job not done) and Worker (payment guaranteed if job done); strongest trust model; no competitor offers this |
| 15% platform commission | Starting point; configurable by Admin |
| Deposit required within 30 minutes of Worker acceptance | Creates urgency to confirm; prevents Worker being held in Busy status indefinitely |
| $10 CAD cancellation fee after confirmation | Compensates Worker for time/travel commitment; deducted from escrow |
| 2-hour dispute window after completion | Gives Requester time to inspect; not so long that Worker waits days for payment |
| "Approve & Release" instant option | Rewards satisfied Requesters with faster release; Worker gets paid sooner |
| 2–3 business day Worker payout | Stripe Connect standard; competitive with TaskRabbit (1–3 days); much better than MowSnowPros (10 days) |
| 15% commission applies to any partial dispute award to Worker | Consistent commission model regardless of dispute outcome |

---

## Ratings

| Decision | Rationale |
|----------|-----------|
| Mutual ratings (both parties rate each other) | Protects Workers from problematic Requesters; builds trust across the platform |
| 1–5 stars with optional text | Industry standard; optional text reduces friction |

---

## Safety & Trust

| Decision | Rationale |
|----------|-----------|
| Background checks required for Workers | TaskRabbit does this; Workers enter private residential properties; essential for trust |
| Mandatory Worker onboarding module | Sets quality expectations; ensures Workers understand liability before first job |
| Age verification (18+) — best effort via DOB + checkbox | Legal exposure if minors transact; payment method requirement provides secondary gate |
| Liability acknowledgement on every In Progress action | Creates clear legal record; Worker cannot claim ignorance of responsibility |
| On-site personnel disclosure | Requester has right to know who is coming to their property |
| Immutable audit records retained 7 years | Liability and legal protection for the platform |

---

## Dispute Resolution

| Decision | Rationale |
|----------|-----------|
| Named "SnowReach Assurance" program | Named guarantee builds user trust (cf. TaskRabbit "Happiness Pledge") |
| 5-business-day Admin ruling window | Gives time for proper review of evidence; not so long as to be frustrating |
| Both parties can submit evidence | Fairness; Worker can respond to dispute claim |

---

## Scope Decisions

| Decision | Rationale |
|----------|-----------|
| Web app only for v1 | Fastest to market; works on mobile browser |
| Native iOS/Android in v2 | Significant additional effort; web first validates the market |
| Tips in v1.1 | Simple to add but deferred to keep v1 scope tight |
| Weather auto-dispatch in v1.1 | High value for Canadian market; moved up from v2 after competitive analysis |
| Price negotiation (counter/accept/reject) in v2 | Adds complexity to UX and logic; v1 keeps pricing simple |
| Local development first, cloud on release | Practical for development phase |
| Git + GitHub for version control | Industry standard; user preference |

---

## Concurrent Job Capacity

| Decision | Rationale |
|----------|-----------|
| Default capacity of 1, user-configurable up to 5 | Default of 1 is safe and simple for MVP; configurable for experienced Workers with helpers or multiple equipment; cap of 5 prevents over-commitment |
| Phase 2 feature | Adds data model and status logic complexity; not needed for MVP validation |
| Data model supports capacity from day one | Avoids rework when Phase 2 ships; capacity field stored as integer defaulting to 1 |
| Count only Confirmed and In Progress jobs | Pending Deposit is not yet a real job commitment; counting it would unfairly block Workers due to slow-paying Requesters |
| Capacity cannot be reduced below active job count | Prevents Workers from accidentally blocking themselves into an inconsistent state |
| Platform maximum of 5 | Prevents Workers from setting unrealistically high capacity and then failing to deliver |

---

## Dispute Resolution

| Decision | Rationale |
|----------|-----------|
| Worker given 48 hours to respond before Admin rules | Fairness; a ruling without hearing the Worker is legally and ethically weak |
| Worker can accept dispute in full without Admin review | Speeds resolution; rewards honest Workers who acknowledge mistakes |
| Evidence package compiled by system | Gives Admin structured, complete information; reduces Admin effort and bias |
| Worker and Requester history included in evidence | Dispute rate and previous outcomes inform but don't determine outcome; protects against bad faith patterns |
| Five distinct ruling options including "Return to complete" and "Increased payment" | Covers the full range of real-world outcomes; partial and conditional rulings are fairer than binary win/lose |
| Increased payment is voluntary for Requester | Platform cannot compel additional charges; Requester must consent; legal and Stripe compliance |
| 15% commission on all amounts disbursed to Worker including partial awards | Consistent commission model; prevents gaming by structuring jobs to trigger disputes |
| No commission on amounts refunded to Requester | Requester should not be charged for a service they didn't receive |
| Fraud thresholds: 20% Worker, 30% Requester dispute rate | Workers are held to tighter standard since they control service quality; Requesters get slightly more latitude before flagging; both are conservative enough to flag only genuine patterns |
| One appeal per party, 48-hour window | Limits frivolous appeals while preserving fairness; second-level review by App Owner ensures quality |
| 7-year immutable record retention | Matches legal requirements for commercial disputes; protects platform against future claims |

---

## Marketplace Bootstrapping

| Decision | Rationale |
|----------|-----------|
| Pre-seed 30–50 Workers before launch | Ensures geographic coverage before first Requester arrives; prevents immediate cold-start failure |
| Geographic staged rollout (3–5 GTA neighbourhoods first) | Dense coverage in fewer zones beats thin coverage everywhere; better early user experience; word-of-mouth spreads within neighborhoods |
| Launch zones Admin-configurable without code deployment | Expansion decisions are operational, not engineering; allows rapid response to demand signals |
| Register-interest page for out-of-zone users | Captures demand signal for expansion prioritisation; doesn't turn users away permanently |
| Admin-assisted monitoring (not manual assignment) | Preserves platform data integrity and UX consistency; human safety net only for edge cases |
| 0% commission on first 10 jobs for early Workers | Immediate, tangible earnings boost; most compelling incentive for supply-side participants |
| 8% commission locked for 12 months for early Workers | Creates urgency to join early; rewards loyalty; platform absorbs difference as CAC |
| Founding Worker badge permanent | Non-monetary recognition that compounds over time as platform grows; costs nothing to maintain |
| First Requester job at 0% commission | Removes financial friction for first-time Requesters; Worker benefits too (gets 100%) |
| $15 Worker referral / $10 Requester referral | Workers are harder to recruit so receive higher bonus; Requesters get platform credit (demand-side) rather than cash |
| Anti-fraud referral rules (same device/IP/payment) | Prevents obvious abuse; referral programs are commonly gamed |
| Bootstrapping success metrics in Admin dashboard | Data-driven expansion decisions; early warning system for supply shortages |

---

## Supply-Side Liquidity

| Decision | Rationale |
|----------|-----------|
| Active declines carry no penalty | Workers making honest decisions should not be punished; declining is better than grudging acceptance; supply is precious — losing Workers is costly |
| Non-responses (timeouts) trigger progressive auto-Unavailable after 3 consecutive | Non-responsive Workers waste Requester time without giving a signal; nudging them to manage their status is better than penalising or removing them |
| Auto-Unavailable is self-reversible with one tap | Keeps Workers in the system; preserves supply; treats Workers as responsible adults |
| Non-response counter resets on next successful job | Avoids permanent stigma for a temporary lapse in responsiveness |
| No email-based blocking for rejection behaviour | Easily circumvented; heavy-handed for a behaviour that costs the platform very little; full bans reserved for ToS violations |
| Simultaneous offers: up to 3 Workers, Requester selects which | Speeds up matching significantly after heavy snowfall events; first-to-accept is fair and simple; cap of 3 prevents flooding the supply pool |
| Requester chooses the 3 (not random or algorithmic) | Respects Requester's view of the ranked list; they may have reasons to prefer certain Workers |
| Already-filled simultaneous non-response excluded from counter | Worker couldn't have accepted anyway; penalising them would be unfair and would erode trust in the system |
| Workers already contacted excluded from refreshed list | Prevents Requesters from re-sending to Workers who already declined; avoids harassment of unwilling Workers |

---

## Legal & Compliance

| Decision | Rationale |
|----------|-----------|
| ToS and Privacy Policy mandatory at registration with active checkbox | Legal protection for platform; establishes informed consent on record |
| Privacy Policy explicitly states address/contact not shared until escrow confirmed | Directly addresses user trust concern; stronger than competitors |
| ToS re-acceptance required on material updates | Ensures users are always bound by current terms |
| Worker insurance — self-declaration only in v1 (not verified) | Certificate verification adds operational overhead; declaration creates legal record and places responsibility clearly on Worker; verification may be added in v1.1 |
| Insurance declaration stored immutably | Legal protection if a Worker later claims they were not informed of the requirement |
| "Insured (Self-Declared)" badge rather than "Insured" | Transparent to Requesters that this is attestation-based, not verified |
| Annual insurance re-declaration | Policies expire; platform needs current information |
| Minimum job price $20 CAD | Protects Workers from underpriced jobs; ensures platform fee is meaningful; slightly below MowSnowPros floor |
| Geographic scope v1: Greater Toronto Area | Validates the market in a large, dense Canadian city before expanding; easier to manage disputes and operations locally |
| Property damage dispute window: 24 hours (vs 2 hours for quality) | Damage may not be immediately visible (e.g., hidden under snow); longer window is fairer to Requesters |
| Requester acknowledges property damage policy before confirming job | Sets expectations; prevents surprise; reduces frivolous disputes |
| Worker acknowledges property damage liability on In Progress | Combined with liability acknowledgement; creates clear legal record at the moment work begins |
| SnowReach not liable for property damage from its own funds | Platform is intermediary; facilitates dispute but does not underwrite damages |

---

## Phased Rollout

| Decision | Rationale |
|----------|-----------|
| Phase 1 uses simple post-completion payments (no escrow hold) | Fastest path to a working MVP; escrow adds significant backend complexity; market validation doesn't require it |
| Escrow and structured disputes deferred to Phase 2 | High-value trust features but require Stripe Payment Intents flow, timed release logic, and formal dispute workflow — better built on a stable Phase 1 foundation |
| Background checks deferred to Phase 3 | Third-party integration with Certn or equivalent; adds operational overhead; not essential for MVP validation |
| Insurance declaration deferred to Phase 3 (with background checks) | Groups all Worker trust/verification features into one phase for coherent UX and a single "trust upgrade" milestone |
| Liability acknowledgement badge introduced in Phase 3 | The badge only makes sense once the full trust framework (background check + insurance) is in place; earlier phases have the acknowledgement but not the formal badge |
| Insurance certificate verification deferred to Phase 4+ | Adds review workflow and document storage complexity; self-declaration in Phase 3 creates the legal record; verification is an enhancement |
| All requirements documented now regardless of phase | Architecture decisions made in Phase 1 must accommodate Phase 2 and 3 features; designing with full knowledge of the roadmap prevents expensive rework |

---

## Payment Exception Handling

| Decision | Rationale |
|----------|-----------|
| Stripe idempotency keys mandatory on all payment operations | Network timeouts are common; without idempotency, a retry creates a duplicate charge. Stripe's idempotency mechanism is the industry-standard solution and costs nothing to implement |
| Durable persistent task scheduling for all timers | In-memory timers are lost on restart. A platform restart mid-timer (auto-release, auto-cancel, auto-refund) would leave jobs permanently stuck. Persistent scheduling is non-negotiable for correctness |
| 3-retry limit with exponential backoff before Admin escalation | 3 retries covers transient failures (network blip, brief API hiccup) without hammering a failing service; exponential backoff prevents thundering-herd on recovery; escalation after 3 ensures human oversight without delay |
| Job state frozen during payment failures (not advanced) | Advancing the state before payment is confirmed creates worse inconsistency (e.g., job shows Released but Worker never paid). Frozen-in-current-state with an Admin exception is the safer invariant |
| Stripe outage: timers paused, not cancelled | Cancelling timers during an outage would cause auto-cancellations when service resumes (30-min window would "expire" during the outage). Pausing and resuming from remaining duration is correct and fair |
| Worker payout funds held up to 90 days | Gives Worker time to fix their banking situation (lost card, account migration); 90 days is generous without being indefinite; legal escalation after 90 days follows Ontario unclaimed property norms |
| Platform credit as first alternative for failed refunds | Platform credit is the fastest and most controllable alternative; avoids off-platform bank transfers for most cases; manual transfer reserved for Requesters who won't use the platform again |
| Phase 1 charge-after-completion risk acknowledged in requirements | Workers should understand the risk when the platform launches in Phase 1; transparency prevents post-hoc complaints; also documents the business case for Phase 2 escrow model |
| Duplicate charge: auto-refund + log, no manual intervention required | Speed matters for duplicate charges — Requester sees two charges and panics; immediate auto-refund restores trust; logging enables post-incident investigation without blocking the refund |

---

## In-App Messaging

| Decision | Rationale |
|----------|-----------|
| Messaging opens at Confirmed (Phase 2) / Worker acceptance (Phase 1) | Both parties have committed at this point; messaging before commitment has no purpose and may generate friction (negotiations belong to the acceptance phase, not after) |
| Messaging closes at terminal state, archived read-only | Completed job threads are valuable records; read-only archive is legally safer than deletion and allows dispute review |
| Messaging stays open during Incomplete state | Worker and Requester need to coordinate when a Cannot Complete is reported; closing the channel at this moment would force them to go through support instead |
| Job revocation via messaging follows same cancellation rules | Revocation is just cancellation from a different entry point; same rules = consistent outcomes; no loophole for fee avoidance |
| Confirmation screen required before revocation | Ensures the initiating party is aware of applicable fees; prevents accidental revocations |
| Contact information prohibition with in-thread reminder | Contact exchange defeats the privacy model (FR-LEGAL-03) and removes the platform's ability to mediate; reminder must be persistent to be effective |
| Messages included in dispute evidence package | Admin needs the full picture; messaging between parties often contains admissions, commitments, or context that is critical to fair dispute resolution |

---

## Cannot Complete Flow

| Decision | Rationale |
|----------|-----------|
| Cannot Complete available in In Progress only | Before In Progress, standard cancellation applies; after In Progress, cannot just cancel — the Cannot Complete flow is the structured exit path |
| Mandatory reason code | Collects structured data for analytics; forces Worker to articulate the reason rather than abandoning silently; reason code distribution distinguishes genuine circumstances (weather) from avoidance patterns (equipment failure repeatedly) |
| Incomplete state with 24-hour Requester resolution window | Gives Requester time to assess and choose; auto-refund as default protects Requester if they're unresponsive |
| Three resolution options (Accept / Refund / Dispute) | Covers the full range of outcomes: Worker did meaningful work (Accept), genuine circumstance (Refund), or Worker negligence (Dispute) — all three are real-world scenarios |
| Auto-refund after 24-hour timeout | Safe default — Requester should not have to fight for a refund when work wasn't completed; Worker's honesty in pressing Cannot Complete should not be used against them by a slow-responding Requester |
| 3 Cannot Complete incidents in 90 days → Admin flag | 3 in 90 days is conservative — flags genuine patterns without penalising Workers for rare equipment problems; reason code analysis helps Admin distinguish weather events from chronic unreliability |
| Cannot Complete tracked separately from dispute rate and cancellation rate | Different behaviours with different implications; conflating them would distort all three metrics and make it harder to diagnose problems |

---

## Dispatcher / Sub-contracting Model

| Decision | Rationale |
|----------|-----------|
| Personal/Dispatcher designation on Worker profile (not sub-accounts) | Crew sub-accounts add significant complexity for v1; the critical thing is Requester awareness and Worker accountability, both achievable with a simple designation flag |
| Requester "Personal Worker only" filter per job (not account-level) | Property owners may prefer personal Workers for their primary home but accept dispatchers for a rental — per-job gives appropriate granularity |
| Account holder fully responsible regardless of designation | Simplifies liability model; consistent with the insurance declaration and liability acknowledgement structure already in the requirements |

---

## Legal Appendices

| Decision | Rationale |
|----------|-----------|
| Three separate appendices (Liability/Insurance, Age, Escrow/Dispute) | Each appendix is shown to users in a different context and at a different time; combining them would bury important terms and weaken the legal record of what was accepted and when |
| Workers acknowledged as independent contractors explicitly | Prevents any employment relationship claim; required for WSIB non-coverage position; must be explicit to be enforceable |
| Occupiers' Liability Act (Ontario) referenced in Appendix A | Workers enter private residential properties; this statute is directly relevant to slip/fall and property access liability; naming it puts Workers on notice |
| WSIB non-coverage acknowledged by Worker | SnowReach does not register Workers with WSIB; this must be disclosed and acknowledged to avoid later claims that Workers expected coverage |
| Insurance declaration is self-attestation only (v1) | Certificate verification adds operational overhead not justified at MVP scale; self-declaration creates a clear legal record and shifts responsibility squarely to the Worker; verification added in Phase 4 |
| Age of Majority and Accountability Act (Ontario) cited | Establishes the 18-year threshold in statute; electronic acknowledgement of DOB + checkbox is the practical enforcement mechanism |
| Electronic Commerce Act, 2000 (Ontario) cited in all appendices | Electronic signatures are legally valid in Ontario under this Act; citing it establishes that checkbox acceptance has the same legal effect as a handwritten signature |
| Escrow described as "conditional intermediary" arrangement, not a trust | Trust arrangements carry specific legal obligations under Ontario law; the escrow here is a contractual payment-holding mechanism only; characterising it as a trust would imply fiduciary duties SnowReach does not intend to assume |
| SnowReach explicitly not liable for property damage from its own funds | Platform is intermediary, not insurer; clear disclaimer protects against claims that the escrow mechanism functions as a damage guarantee |
| Governing law: Ontario courts, Ontario and Canadian law | Operational jurisdiction is GTA; Ontario courts are the appropriate and most practical venue |
| Legal review disclaimer on all appendices | These are draft templates; platform operators bear legal risk if deployed without review; the disclaimer is both practical and good-faith disclosure |

---

## Competitive Positioning

| Decision | Rationale |
|----------|-----------|
| Worker-set tier pricing (not customer bid) | More predictable for Workers; distinguishes from SnowMowGo/MowSnowPros |
| Job scoping photos at request time | No competitor offers this; helps Workers assess jobs before accepting |
| Currency: CAD, Distance: km | Canadian market focus for v1 |

---

## Analytics & Optimization

| Decision | Rationale |
|----------|-----------|
| Analytics data collection starts Phase 1 | Metrics collected from day one yield more data for Phase 2 optimizations; retrofitting tracking is costly |
| Full analytics dashboard in Phase 2 | Insufficient job volume in early Phase 1 to generate meaningful signals; dashboard is most valuable once data exists |
| Acceptance rate as secondary ranking factor (after rating) | Workers who consistently accept jobs deliver better Requester experience than those who pick and choose; secondary (not primary) to preserve rating as the trust signal |
| Acceptance rate excluded for Workers with fewer than 5 jobs | Too few jobs to compute a statistically meaningful rate; avoids penalising new Workers who have not yet had enough offers |
| All ratings publicly visible (Worker and Requester) | Builds trust symmetrically on both sides of the marketplace; Workers can make informed acceptance decisions based on Requester reputation |
| Rating prompt delayed 1 hour after completion | Gives Requester time to inspect work before rating; reduces snap judgements immediately post-completion |
| Rating thresholds (flag at 4.0, suspend at 3.5, min 10 jobs) | Below 4.0 is a meaningful quality signal at scale; 3.5 represents sustained poor performance that warrants intervention; 10-job minimum prevents outlier early ratings from triggering unfair consequences |
| Rating thresholds apply equally to Workers and Requesters | Mutual accountability; platform protects both sides |
| Pricing heat map deferred to post-v1 | Insufficient data in v1; requires native app map UX to be useful; Workers can use Admin zone metrics as a proxy |

---

## Geolocation & Matching

| Decision | Rationale |
|----------|-----------|
| Google Maps Geocoding API as primary method | Best-in-class address coverage for Canada; well-documented; consistent with Google Maps for distance display |
| Postal code FSA centroid as first fallback | Canada Post postal codes are granular enough for neighbourhood-level matching; available from open data; functional without requiring user action |
| GTA neighbourhood dropdown as second fallback | Graceful degradation that keeps matching functional even when APIs are unavailable |
| Cache geocoded coordinates on address record | Eliminates repeated API calls for the same address; ensures matching continues during API outages |
| 10% service radius buffer opt-in, off by default | Workers should not be surprised by out-of-area offers; opt-in preserves choice; default off prevents accidental over-commitment; clear labelling on each offer ensures informed decision |
| Service area radius only (no custom polygon) for v1 | Polygon drawing requires native app map interaction (unavailable in v1 web app); radius is simpler to implement and sufficient for MVP |
| Launch zone hard boundary enforced at matching | Prevents thin supply from being spread across too large an area in early launch; enforcement at the matching layer (not just display) prevents edge cases |

---

## Service Standards

| Decision | Rationale |
|----------|-----------|
| Cleared to pavement as the completion standard | Unambiguous, visually verifiable, and defensible in a dispute; subjective standards ("mostly clear") invite gaming and endless disputes |
| Snow placement constraints enumerated explicitly | Fire hydrants, catch basins, and neighbour access are safety and legal issues; making these explicit in requirements and the Appendix D user-facing text reduces the Admin's interpretive burden in disputes |
| Ice treatment excluded from default scope | Ice treatment requires different equipment and materials than snowblowing; conflating the two would create scope ambiguity on every job; opt-in by explicit agreement prevents unexpected disputes |
| Completion assessed at moment of marking Complete | Makes the completion determination objective and time-anchored; removes ambiguity about subsequent weather events; protects Workers from disputes about conditions they did not cause |
| Site access failure not counted as Worker Cannot Complete | Worker's reliability record should reflect the Worker's performance, not circumstances entirely outside their control; unfair to penalise Workers for locked gates or blocked driveways |
| 30-minute arrival tolerance before violation | Tight enough to protect Requesters' time, loose enough to absorb normal delays; requires prior messaging notice rather than unilateral tardiness |
| GTA noise bylaw hours (07:00–23:00) codified in standards | Municipal law already governs this; codifying it in platform standards makes it enforceable through the dispute process without requiring legal action |

---

## Administrator Authority and Audit Log

| Decision | Rationale |
|----------|-----------|
| Administrator authority made explicit in ToS and requirements | Ambiguity about who has final authority in disputes is a common source of user frustration; making it explicit in both the ToS and the Appendix D user-facing text removes that ambiguity and reduces appeal rates |
| Administrator may act without prior notice in urgent cases | Platform integrity can require immediate account suspension (e.g., fraud); requiring advance notice would enable bad actors to withdraw funds or take other actions before the suspension takes effect |
| Audit log is append-only with hash chaining | Two separate integrity properties: append-only prevents deletion; hash chaining detects any modification to historical records. Both are needed because a corrupted audit log undermines all dispute and compliance processes |
| Audit log in a separate write-once store from operational DB | Prevents a compromised operational database from affecting the audit trail; separation also enables different retention and backup policies |
| All state-changing events logged before state change is committed | Ensures the audit log is always ahead of or synchronized with system state; prevents gaps caused by write failures after a state transition has occurred |
| 7-year log retention | Consistent with NFR-07 for acknowledgement records; sufficient for tax, insurance, and legal purposes in Ontario; matches the retention period for financial records |
| Daily hash chain integrity check | Balances detection speed with computational cost; a daily check catches tampering before it can be relied upon for dispute or compliance purposes |

---

## Administrator Monitoring and Flags

| Decision | Rationale |
|----------|-----------|
| Structured flag registry (not ad hoc alerts) | Consistency matters for fairness — the same behaviour should trigger the same review for every user; a formal registry makes the trigger conditions auditable and prevents gaps |
| Three severity levels (Informational / Warning / Critical) | Allows Admin workload to be triaged; Critical flags get same-day attention, Warnings get 3-day SLA, Informational items are logged for pattern analysis without demanding immediate action |
| Auto-suspension on WF-02 (rating below 3.5) | Consistent with BR-33; automating this removes human delay and potential bias from a clear threshold breach |
| WF-05 auto-Unavailable on 3 consecutive non-responses | Consistent with BR-21; Unavailable status is reversible by the Worker; protects Requesters from a Worker who has gone silent |
| WF-07/08 job blocking for background check / insurance gaps | Phase 3+ only; these are trust signals that must be gated at job acceptance, not just at registration |
| JF-02 auto-refund after 24 hours in Incomplete state | Consistent with BR-34 and FR-JOB-14; protects Workers from being held in limbo indefinitely; auto-refund is the fairest default given the Worker raised the Cannot Complete |
| JF-03 escalation to App Owner after 5 business days in Disputed state | Disputes should not age without resolution; escalation to App Owner ensures a second set of eyes on stale cases |
| PF-03 immediate alert on audit log integrity failure | An audit log failure is a security event, not an operational incident; it requires the highest priority response |
| Platform-wide acceptance rate flag (PF-04) at 60% | A platform acceptance rate below 60% signals that supply is failing to meet demand in active zones; this is an early warning of marketplace health problems that require bootstrap interventions |

---

## Escrow Moved to Phase 1

| Decision | Rationale |
|----------|-----------|
| Escrow required from Phase 1 (no post-completion payment model) | Collecting payment after a Worker has already performed work creates unacceptable financial risk for Workers and no protection in the event of payment failure; the escrow model is not technically more complex than post-completion charging, and Stripe's Payment Intents API handles both in the same architecture |
| FR-PAY-22 (Phase 1 post-completion retry) removed entirely | This requirement described a workaround for a payment model that no longer exists; removing it eliminates a source of developer confusion |
| Phase 2 renamed from "Escrow & Structured Disputes" to "Structured Disputes & Worker Onboarding" | Escrow is now Phase 1; Phase 2 adds the structured SnowReach Assurance dispute workflow, Worker onboarding module, and concurrent job capacity |
| Messaging thread opens at Confirmed (escrow deposit received) from Phase 1 | With escrow in Phase 1, Confirmed is a well-defined state from the start; no need for a Phase 1 / Phase 2 split in messaging activation |

---

## Dispatcher Worker WSIB and Personal Injury Coverage

| Decision | Rationale |
|----------|-----------|
| Dispatcher Workers must carry employer-equivalent WSIB/personal injury coverage | A Personal Worker working alone carries personal accident risk for themselves; a Dispatcher sending crew members incurs employer-equivalent obligations under Ontario's WSIB Act for those workers — failure to carry this coverage exposes the Dispatcher to significant personal liability |
| WSIB coverage obligation stated in FR-WORK-19 and Appendix A.4 | Placing this in both the functional requirement and the legal acknowledgement ensures it appears at registration and at the point of job execution |
| Dispatcher-specific insurance declaration deferred to Phase 3 (FR-WORK-20) | Phase 3 introduces the insurance declaration for all Workers; Dispatcher-specific coverage requirements are part of that same phase; applying it earlier would require Phase 1 to validate insurance, which is Phase 3 scope |

---

## HST on Top of Negotiated Price

| Decision | Rationale |
|----------|-----------|
| HST is payable by the Requester on top of the tier price (not embedded) | Embedding tax in the price is legally problematic for HST-registered Workers who must remit HST on their taxable supplies; showing HST as a separate line item creates a valid tax invoice and makes the Worker's tax obligation clear |
| Commission calculated on pre-tax price only | The 15% platform commission is a service fee on the job price; HST is a tax collected on behalf of the CRA, not income to the platform; taxing a tax would be incorrect and non-compliant |
| HST passed through 100% to Worker | The platform is not the supplier of the snow clearing service; the Worker is; HST collected belongs to the Worker (to remit to CRA); the platform's role is to collect it correctly and pass it through |
| Workers self-declare HST registration status | Not all Workers will be HST-registered (threshold is $30,000 annual revenue); requiring a declaration rather than assuming all Workers charge HST avoids incorrect tax charges for unregistered Workers |
| Worker Business Number displayed on Requester receipt | Required for the receipt to constitute a valid HST/tax invoice under the Excise Tax Act; Requesters who are themselves businesses need this for input tax credits |

---

## PIPEDA Compliance

| Decision | Rationale |
|----------|-----------|
| Full PIPEDA section added as Section 3.23 | PIPEDA applies to all commercial organizations collecting personal information from Canadians; SnowReach collects name, address, date of birth, payment data, and location — all categories of personal information under PIPEDA; compliance is mandatory, not optional |
| 72-hour OPC breach notification | This is the PIPEDA requirement for breaches creating real risk of significant harm; the timeline is statutory |
| Personal profile data deletable on request; audit/job/payment records exempt | PIPEDA permits retention where required by law or legitimate business purposes; 7-year financial record retention is a legitimate business and legal requirement; deleting those records would undermine audit integrity |
| Cross-border transfer disclosure (Firebase, Stripe — US processors) | PIPEDA's accountability principle requires disclosure when personal information is transferred to processors in other jurisdictions; Firebase and Stripe are US-based; Ontario users' data crosses the border |
| Privacy Officer designated (initially App Owner) | PIPEDA requires a designated individual accountable for compliance; this is a statutory role |

---

## Appeal Structure and App Owner Oversight

| Decision | Rationale |
|----------|-----------|
| Appeals reviewed by a designated senior Administrator, not the App Owner | Having the App Owner review appeals created a circularity (especially when App Owner = Administrator); using a senior Administrator preserves platform-internal independence without requiring an external third party |
| The same Administrator may not rule on both the original dispute and the appeal | Structural independence requirement; an Administrator reviewing their own ruling is not a meaningful review |
| App Owner holds superior authority and may direct reconsideration | The App Owner remains the ultimate authority and must have a mechanism to correct material errors, legal risks, or misconduct; this authority is reserved for exceptional circumstances and is always logged |
| App Owner has read access to all records but does not issue rulings | Separates oversight from adjudication; the App Owner can review everything and direct reconsideration, but putting the App Owner in the ruling chain would make them a party to every dispute, which conflicts with their role as platform operator |

---

## On-Site Personnel Challenge Right

| Decision | Rationale |
|----------|-----------|
| Requester may decline an unexpected on-site person via in-app action | A Requester's property is their home; they have the right to know and approve who enters it; the platform disclosed a specific name, and a different person showing up violates that expectation |
| 15-minute response window, then auto-accept | The Worker may be standing at the door; an indefinite response window is impractical; 15 minutes is enough for a Requester who is monitoring the app; auto-accept preserves workflow continuity for Requesters who are not present |
| No cancellation fee on personnel decline | The Requester did not choose to cancel; the Worker triggered the situation by sending an undisclosed person; holding the Requester financially responsible would penalise the wrong party |
| Edge cases left to Administrator determination | Contested scenarios (Worker claims same person was disclosed; Requester disputes the name disclosed vs. who arrived) are inherently fact-specific; the Administrator has the audit log and the in-app disclosure record to adjudicate |

---

## Dispute Window Extended to 4 Hours

| Decision | Rationale |
|----------|-----------|
| 4-hour quality dispute window (from 2 hours) | 2 hours is insufficient for a working Requester who may not check their phone immediately after a 7 AM job completion; 4 hours provides meaningful protection without unreasonably delaying Worker payouts; property damage disputes remain at 24 hours |

---

## Insurance Coverage Minimums

| Decision | Rationale |
|----------|-----------|
| Personal Workers: $1,000,000 CAD minimum | Standard minimum for residential service contractors in the Toronto area; sufficient for property damage to a typical residential driveway or adjacent vehicle |
| Dispatcher Workers: $2,000,000 CAD minimum | Dispatchers send crew members, creating employer-equivalent exposure; $2M is the standard commercial general liability minimum for Ontario contractors with workers; covers both property damage and worker injury claims |
| Minimums enforced at declaration (self-attested) | Workers select their designation and confirm the applicable minimum applies; SnowReach does not independently verify coverage amount in Phase 3 (verification is Phase 4+) |

---

## Start Time Field

| Decision | Rationale |
|----------|-----------|
| Start time is an optional structured field (not free text) | A formal field creates a record in the job data that the system and the Admin can reference in disputes; free text in notes is informal and easily disputed; both fields (earliest and latest start) are optional to preserve flexibility |
| Worker acknowledges start time window at acceptance | The time constraint is part of the agreed job terms; requiring acknowledgement at acceptance prevents Workers from claiming they did not see the window |
| No timing obligation when no window is specified | Requester chose not to specify; imposing an implicit timing standard would be unfair; Administrator determines reasonable timing in any edge case |

---

## Account Deletion

| Decision | Rationale |
|----------|-----------|
| Account deletion permitted when all obligations are clear | Deleting an account mid-job or mid-dispute would leave funds and outcomes unresolved; the outstanding-obligations gate prevents this |
| "Complete, abandoned, or dispute resolved" as the gate | These are the only terminal conditions that definitively resolve financial obligations; any other state has a pending outcome |
| Personal data deleted/anonymized within 30 days | PIPEDA requires timely deletion; 30 days allows for backend processing and confirmation |
| Audit, job, and payment records retained 7 years | Financial and legal retention requirements; these records may be needed for tax, insurance, or legal purposes long after the user has left the platform; anonymization preserves record integrity without retaining personal identifiers |

---

## Edge Case Policy

| Decision | Rationale |
|----------|-----------|
| Edge cases in dispute resolution, Cannot Complete resolutions, and on-site personnel challenges are intentionally lightly defined | Prescribing every edge case in requirements creates rigid rules that may produce unfair outcomes in unanticipated circumstances; the Administrator is better positioned to exercise judgment given the full facts; if patterns emerge that warrant standardization, specific rules can be added in a future version |
