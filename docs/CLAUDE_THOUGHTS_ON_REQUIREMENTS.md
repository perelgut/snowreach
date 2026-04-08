<p align="center"><img src="../images/YoSnowMow.png" alt="YoSnowMow" height="120" /></p>

# Claude Thoughts on Requirements
## SnowReach — Requirements v2.1 Review
**Date:** 2026-03-28
**Reviewer:** Claude (Senior Developer perspective)
**Method:** Three-pass review — Administrator, Worker (Personal and Dispatcher), Requester

---

> This document records observations made during a close reading of REQUIREMENTS.md v2.1
> from three operational perspectives. Items are flagged as **Gap**, **Conflict**,
> **Confusion**, or **Legal** concern. All items are recommendations for the user's
> consideration — none are unilateral changes. The user should decide which items to
> address before approving the requirements.

---

## PASS 1 — Administrator Perspective

*Reviewing as an Administrator who must configure the platform, review disputes, manage
flags, enforce service standards, and be accountable for all rulings.*

---

### A1. [Conflict] Administrator Authority vs. Appeal Process Creates a Loop

**Location:** FR-AUTHZ-02 and FR-DISP-16

FR-AUTHZ-02 states that Administrator determinations "are not subject to reversal by any
other user, including the App Owner, except through the formal appeal process." However,
FR-DISP-16 states that appeals are reviewed by "the App Owner or a designated senior
Administrator." This means the App Owner can reverse the Administrator's ruling — which
directly contradicts FR-AUTHZ-02's carve-out language.

As written, these two requirements say: "only the appeal process can reverse the Admin"
*and* "the appeal process is decided by the App Owner." This means the App Owner *can*
reverse the Administrator. That's likely the intended outcome, but the language in
FR-AUTHZ-02 appears to prohibit it. Recommend either removing "including the App Owner"
from FR-AUTHZ-02, or clarifying that the App Owner's authority is superior to the
Administrator's in the appeal context.

---

### A2. [Gap] No Accountability Mechanism for Administrator Misconduct

**Location:** Section 3.20, Appendix C.6

The requirements establish Administrator authority as final within the platform, and
appeals go to the App Owner. But Section 2 explicitly notes that Administrator and App
Owner "may overlap." If the same person holds both roles, there is no independent review
path for a user who believes an Administrator acted improperly (with bias, in error, or
in bad faith). The appeal ruling is "final and binding" with no external recourse
mentioned — although C.6 §3 does preserve the right to pursue claims "through other
means" (the courts).

This is a legal and reputational risk. Recommend adding at minimum: a conflict-of-interest
declaration (the Administrator must recuse themselves from disputes where they have a
personal connection to either party), and a clause that the App Owner may not serve as
Administrator in any dispute where they are also the appellant reviewer.

---

### A3. [Conflict] Fraud Detection Thresholds Are Inconsistent

**Location:** FR-DISP-12 vs. FR-DISP-13/14, WF-03, RF-03, BR-26

The fraud detection thresholds in FR-DISP-12 contradict the thresholds in every other
location in the document:

| Location | Worker Dispute Threshold | Requester Dispute Threshold |
|---|---|---|
| FR-DISP-12 | **15%** | **25%** |
| FR-DISP-13 | 20% | — |
| FR-DISP-14 | — | 30% |
| WF-03 / RF-03 (flags) | 20% | 30% |
| BR-26 | 20% | 30% |

FR-DISP-12 is the outlier and should be updated to match the 20%/30% values used
everywhere else, or a deliberate distinction should be made (e.g., FR-DISP-12 is an
early alert; FR-DISP-13/14 is the action threshold).

---

### A4. [Conflict] BR-02 Is Superseded but Not Updated

**Location:** BR-02 vs. BR-32 and FR-REQ-02

BR-02 states: *"Workers ranked by rating (desc) then distance (asc). Price not a ranking
factor in v1."* BR-32 correctly states the three-factor ranking: (1) rating, (2)
acceptance rate, (3) distance. FR-REQ-02 also reflects the correct three-factor ranking.
BR-02 is factually incorrect as written and contradicts the other two sources. An
Administrator or developer reading BR-02 in isolation would implement the wrong ranking.
Recommend updating BR-02 to match BR-32, or explicitly marking it as "see BR-32."

---

### A5. [Gap] No Consequence Defined for Failed "Return to Complete" Ruling

**Location:** FR-DISP-09, Section 3.17.4

The "Return to complete" ruling type requires the Worker to return within 24 hours. The
requirement states that if the Worker fails to return, the ruling "converts to full
refund." However, it does not specify:

- Whether the failure-to-return is counted as a dispute lost by the Worker (affecting
  their dispute rate and analytics)
- Whether the failure-to-return triggers a reliability flag (similar to Cannot Complete)
- Whether the Worker retains the right to appeal the converted ruling
- Whether the Requester's property has potentially received more snowfall that the
  Worker is not expected to clear

The Administrator needs defined consequences to apply consistently. Without them, two
Administrators might handle this case differently.

---

### A6. [Gap] Increased Payment Ruling Has No Response Deadline

**Location:** FR-DISP-09, FR-DISP-11

The "Increased payment" ruling invites the Requester to authorize an optional additional
charge. But there is no deadline specified for the Requester's response to this
invitation. Does the escrow remain frozen while waiting? If the Requester takes 10 days
to respond, the Worker's payout is delayed by 10 days beyond the 5-business-day ruling
SLA. The Administrator needs a defined window (suggest: 48 hours to respond, then
proceed on the escrowed amount only).

---

### A7. [Gap] Service Standard Violation Has No Dedicated Flag

**Location:** Section 3.19, Section 3.22

Section 3.19 defines nine service standards (noise bylaws, snow placement, operating
hours, etc.). A Worker who violates FR-SVC-07 (operates equipment before 07:00) or
FR-SVC-02 (places snow on a neighbour's driveway) has violated a service standard. But
the flag registry in Section 3.22 has no "Service Standard Violation" flag. The only
path to flagging this is through a dispute ruling, which is a blunt instrument. Recommend
adding a Worker flag WF-09: "Administrator records a service standard violation finding
against a Worker" (Warning; no auto-action). This allows the Admin to document a pattern
even when the full dispute amount is small.

---

### A8. [Gap] Phase 1 Dispute SLA Is Undefined

**Location:** Section 8 (Phase 1), Section 3.13, Section 3.17

Phase 1 dispute handling is explicitly described as: "Manual: Requester contacts Admin;
Admin reviews and resolves; no formal SLA or structured workflow." The 5-business-day
SLA is introduced in Phase 2 with structured SnowReach Assurance. But there is no
acknowledgment of what Phase 1 users are told when they contact the Admin. Are they
told "we'll get back to you when we can"? Is there an informal target? Platform
credibility in Phase 1 depends on responsive dispute handling even without a formal SLA.
Recommend at minimum adding a Phase 1 admin target (e.g., "best-effort within 5 business
days") or explicitly noting in Section 8 that Phase 1 dispute response time is at Admin
discretion.

---

### A9. [Confusion] Section Numbers Are Out of Order in the Document

**Location:** Sections 3.14 through 3.22

The section numbering in the document does not match the document's physical layout:
- Sections 3.17 (Dispute Resolution) and 3.18 (In-App Messaging) appear in the file
  *before* Sections 3.14 (Terms of Service), 3.15 (Insurance), and 3.16 (Property
  Damage).
- FR-ANLT-21 (pricing intelligence) appears *after* FR-ANLT-22 (Cannot Complete rate)
  in the Analytics section.

This will cause confusion during specification writing and development. Recommend
renumbering and reordering before the Specification Document phase — at that point the
section numbers will be used as cross-references throughout the specification.

---

### A10. [Confusion] Cannot Complete "Accept Outcome" Releases Full Payment

**Location:** FR-JOB-13

Option (a) of the Incomplete resolution — "Accept outcome: Requester releases payment to
the Worker" — releases the full escrowed amount via "standard completion rules (85%
Worker, 15% App Owner)." This means a Worker who cleared 40% of a driveway and then
reported Cannot Complete (equipment failure) can receive full payment if the Requester
chooses "Accept outcome."

This is likely intentional — the Requester is choosing to pay in full for partial work
out of goodwill — but it is not made explicit. The requirement should clarify: "The
Requester is choosing to release full payment for whatever work was completed. No partial
payment option is available in this flow; partial payment is only available via the
dispute process." This protects the Administrator from questions about why a Worker was
paid in full for a cannot-complete job.

---

## PASS 2 — Worker Perspective (Personal and Dispatcher)

*Reviewing as a Worker who wants to understand financial exposure, legal obligations,
operational rules, and fairness of the system before signing up and performing jobs.*

---

### W1. [Legal] Phase 1 Financial Exposure Is Severe for Workers

**Location:** FR-PAY-22, Section 8 Phase 1

In Phase 1, the Worker performs all work *before* payment is collected. FR-PAY-22
acknowledges that if the post-completion charge fails after 3 retries over 24 hours, the
"Admin is alerted and the job is marked as a payment exception" — but there is no
requirement that the Worker actually gets paid. The Worker has done the work and has no
defined recourse. The document notes this is "a known limitation" and "a primary
motivation for the Phase 2 escrow model," which is accurate — but it leaves the Worker
with no protection.

Consider whether Phase 1 should at least include a pre-authorization hold (a charge
authorization placed before work begins, captured after completion), which eliminates
most of the payment risk without requiring the full escrow architecture. As written, a
Worker who accepts a Phase 1 job from a bad-faith Requester has no financial protection
and no mechanism for recovery through the platform.

---

### W2. [Legal] Dispatcher Workers — Sub-contractors' Personal Injury Exposure

**Location:** Appendix A.2, A.4, FR-WORK-19

Appendix A.2 §4 tells Workers that SnowReach does not register with WSIB on their behalf
and that Workers are "solely responsible" for any coverage they require under the WSIB
Act. For a **Personal Worker**, this means they need personal accident insurance for
themselves. For a **Dispatcher Worker** sending out crew members, the WSIB exposure is
significantly more complex.

If a Dispatcher's crew member is injured on site, the Dispatcher's liability as the
registered account holder is clear in the platform's terms — but the injured crew member
may have no workers' compensation coverage unless the Dispatcher has registered as an
employer with WSIB or has a crew member who is themselves WSIB-registered. The insurance
declaration (FR-INS-02) says Workers must have "adequate personal liability insurance
covering all... third-party claims arising from snow clearing and related work performed
by any person on my behalf" — but this describes *property damage and injury to others*,
not *injury to the crew member themselves*.

The requirements should explicitly note that Dispatcher Workers bear full WSIB and
employer-equivalent obligations for their crew members, and that the insurance declaration
should specifically address coverage for persons working on the Dispatcher's behalf.
This is a significant legal gap.

---

### W3. [Legal] "Adequate Insurance" Is Undefined

**Location:** FR-INS-02, FR-WORK-08, Appendix A.5

The insurance declaration requires Workers to declare they have "adequate personal
liability insurance." "Adequate" is never defined — no minimum coverage amount is
specified anywhere in the requirements. A Worker with a $100,000 homeowner's endorsement
and one with a $2,000,000 commercial policy both satisfy this declaration. But from a
Requester's perspective (and from a platform liability standpoint), these are vastly
different.

For v1 self-declaration, this ambiguity may be intentional. But the requirements should
at least acknowledge that "adequate" is undefined and that the platform makes no
representation to Requesters about coverage levels. The "Insured (Self-Declared)" badge
(FR-INS-06) creates a perception of protection that may not exist. Recommend adding a
one-sentence platform disclaimer to the badge: "SnowReach has not verified the amount
or validity of any Worker's insurance."

---

### W4. [Conflict] Insurance Declaration Phase Assignment

**Location:** FR-WORK-08, BR-18, Section 8 Phase Summary

This is a direct contradiction:
- **FR-WORK-08** states: "A Worker shall declare that they carry adequate personal
  liability insurance... before their account is activated for paid work." No phase is
  cited; this implies Phase 1.
- **BR-18** states: "Workers must complete an insurance declaration before their first
  job." Phase 3.
- **Phase Summary table**: "Insurance declaration + badge" → Phase 3.

Three locations conflict. FR-WORK-08's language — "before their account is activated for
paid work" — unambiguously implies Phase 1. If insurance declaration is Phase 3, FR-WORK-08
must be updated to add a "(Phase 3)" designation. If it is Phase 1, BR-18 and the Phase
Summary table must be corrected. This needs a decision.

---

### W5. [Gap] Non-Response Counter Reset Condition Is Ambiguous

**Location:** FR-WORK-16

The non-response counter resets "when a Worker successfully accepts and completes a job
after re-enabling their status." The word "completes" creates an edge case: what if the
Worker accepts a job that is subsequently cancelled (by the Requester, before In Progress)?
The Worker responded correctly — they accepted — but the job never completed. Does the
counter reset? If not, the Worker is penalized for a circumstance outside their control.
Recommend the reset condition be "when a Worker successfully accepts a job that reaches
Confirmed status," rather than requiring completion.

---

### W6. [Gap] Job Posting Has No Start Time Field, But Timing Is a Service Standard

**Location:** FR-REQ-01, FR-SVC-08

FR-SVC-08 defines a service standard violation if the Worker arrives more than 30 minutes
outside "the agreed window." But FR-REQ-01, which defines what a Requester puts in a job
posting, does not include a start time field. Start time is listed only as free-text in
"optional notes." A Worker could accept a job, miss a start time buried in notes, and be
held to a service standard they had no structured way to acknowledge. Either start time
should be a formal field in the job posting (FR-REQ-01) with explicit Worker acceptance
at the offer stage, or FR-SVC-08 should clarify that the timing standard only applies
when a start window is entered in the formal time field (not buried in notes).

---

### W7. [Gap] Platform Does Not Enforce Operating Hours

**Location:** FR-SVC-07, D.6

FR-SVC-07 and D.6 prohibit work before 07:00 and after 23:00. But the requirements
include no technical enforcement — nothing prevents a Worker from tapping "Mark In
Progress" at 05:30 AM. This is a policy rule with no platform guardrail. Workers who
aren't aware of the bylaw (e.g., a Worker new to the GTA) could inadvertently violate
it, receive a noise complaint from a neighbour, and face both a bylaw penalty and a
service standard violation on their platform record. Recommend requiring the platform to
block the "Mark In Progress" action outside permitted hours, or at minimum display a
prominent warning.

---

### W8. [Confusion] Return-to-Complete Ruling — Cost and Scope to Worker

**Location:** FR-DISP-09 (ruling type: Return to complete)

The "Return to complete" ruling directs the Worker to return to the property within
24 hours with no additional compensation. From the Worker's perspective:

- They receive no additional payment for the return trip, time, or fuel.
- If it has snowed again since the original job, are they expected to re-clear new snow?
  The requirements don't say.
- If the Worker has accepted another job in the interim (Phase 2: configurable capacity),
  do they have to cancel that job to comply?
- The Worker cannot appeal the ruling until after they've either complied or failed to
  (and triggered the "converts to full refund" consequence).

This ruling type is operationally complex and one-sided. The requirements should clarify:
(a) return visit covers only the originally agreed scope, not new snowfall; (b) the
Worker's ability to accept new jobs is not affected during the 24-hour return window;
(c) the Worker may appeal the ruling within 48 hours of notification even if they intend
to comply.

---

### W9. [Gap] Rating After a Won Dispute Creates Perverse Incentive

**Location:** FR-RATE-06

Rating prompts fire "immediately after a dispute is resolved." If the Worker wins the
dispute in full, the Requester — who may be frustrated at losing — can immediately submit
a 1-star rating. This lets a bad-faith Requester use the dispute process as a mechanism
to leave a damaging rating with impunity.

Consider: ratings after a full-Worker-victory dispute should either be blocked, deferred
further, or flagged for Admin review. At minimum, if a Requester's dispute is fully
rejected and they then submit a 1-star rating, that pattern (FR-DISP-12: "dispute filed
within minutes of job completion on multiple occasions") could be extended to cover
rating abuse.

---

### W10. [Confusion] Dispatcher Designation — Can It Change Mid-Job?

**Location:** FR-WORK-19, FR-REQ-13

The requirements don't address whether a Worker can change their Personal/Dispatcher
designation after accepting a job. If a Worker is accepted as Personal and then switches
their designation to Dispatcher before marking In Progress, they could send a crew member
to a job the Requester expected a personal visit for (even if the Requester didn't apply
the "Personal only" filter). Recommend: the Worker's designation shown on the accepted
job offer should be frozen for the duration of that job, regardless of later changes to
the account designation.

---

### W11. [Confusion] Dispatcher Workers — Who Receives the Rating?

**Location:** Sections 3.8, FR-WORK-19

It is not explicitly stated that ratings attach to the registered account holder
regardless of who performed the physical work. A Requester rates the job, and the Worker
on the job may be a crew member — but the rating goes to the Dispatcher's account.
This is the correct behavior, but it should be stated explicitly (e.g., in Section 3.8
or FR-WORK-19) because it has implications for how Workers understand their rating score.
A Dispatcher who sends poor crew members but is never personally on-site will still see
their rating fall.

---

### W12. [Gap] No Scope Clarification Mechanism Before Acceptance

**Location:** FR-REQ-04, FR-REQ-05

When a Worker receives a job offer, they have 10 minutes to Accept or Decline. There is
no mechanism to request clarification about job scope before committing. Workers must
either accept a job they're uncertain about or decline it (which counts toward their
acceptance rate). Requesters use free-text notes and optional photos to describe the
job, but this is informal. A Worker who declines a poorly described job loses an
acceptance. Consider: should Workers be able to send a pre-acceptance question via
messaging? (Note: messaging currently only opens at Confirmed/accepted status per
FR-MSG-01.) This is a workflow gap that the specification phase should address.

---

## PASS 3 — Requester Perspective

*Reviewing as a property owner who wants to book a reliable Worker, have their driveway
cleared well, and not be exposed to unexpected charges or unsafe situations.*

---

### R1. [Gap] 2-Hour Dispute Window Is Very Short

**Location:** FR-JOB-06, FR-ASSUR-02, FR-DISP-01

The quality/completion dispute window is 2 hours from job completion. For a job completed
at 7:00 AM when the Requester has already left for work, the window closes at 9:00 AM.
The Requester may not see the completion notification until lunch. By then, funds have
been auto-released. This is a consumer protection issue, particularly for working
Requesters, seniors, and anyone who may not check their phone constantly.

Consider whether a "business hours window" extension or a minimum absolute window (e.g.,
"2 hours or until 9:00 AM the next business day, whichever is later") would better
protect Requesters without significantly increasing risk to Workers. The current 2-hour
window is borrowed from rideshare paradigms where the customer is present for the
service. Snow clearing is different — the Requester is often not present.

---

### R2. [Gap] No Recourse When Undisclosed Person Appears Onsite During In Progress

**Location:** FR-LIAB-03, FR-LIAB-04, FR-JOB-09

If a Worker marks In Progress and then discloses an undisclosed third party (FR-LIAB-03),
the Requester is notified (FR-LIAB-05/06). But at In Progress, the cancellation rules
in FR-JOB-09 say "Standard cancellation is not permitted." The only cancellation options
are Cannot Complete (a Worker action) or raising a dispute (after completion).

A Requester who is uncomfortable with an undisclosed stranger on their property has
no immediate platform remedy. Messaging is available (and "Revoke Job" is an option per
FR-MSG-05), but the fee rules are ambiguous — FR-MSG-05 says the revocation "follows the
same cancellation rules applicable to the current job state," and FR-JOB-09 says
cancellation is not permitted at In Progress. This appears to mean revocation via
messaging is also blocked at In Progress. If so, the Requester is trapped.

This is a safety and trust gap. Recommend: a Requester should retain the right to cancel
a job at the moment of an undisclosed-person notification, without penalty, regardless
of job state.

---

### R3. [Gap] No Formal Start Time Field in Job Posting

**Location:** FR-REQ-01, FR-SVC-08

The job posting form (FR-REQ-01) does not include a start time field — only free-text
notes and optional images. Yet FR-SVC-08 defines a service standard violation based on
the Worker missing "the agreed window." If the agreed window lives only in the notes
field, it has no formal status in the system. It cannot be pulled into the Admin's
evidence package with a timestamp, cannot be enforced programmatically, and can be
disputed ("I didn't see it in the notes").

Recommend adding a formal optional start time / time window field to the job posting
structure (FR-REQ-01), with the Worker explicitly acknowledging the time window at the
point of acceptance.

---

### R4. [Gap] Completion Photos Are Optional for Workers

**Location:** FR-JOB-05

Workers "may attach up to 5 completion photos" when marking a job Complete. Photos are
optional. If a Worker marks a job Complete without photos, the Requester's evidence in
any dispute is severely disadvantaged. The Worker can claim they completed the work; the
Requester has no visual record. Consider making completion photos mandatory (or at least
strongly encouraged with a confirmation prompt) — particularly for Dispatcher Workers
who are not personally on-site and whose crew members' work cannot be verified without
visual evidence.

---

### R5. [Gap] Requester Cannot Proactively Request a Specific Worker

**Location:** Section 3.4 generally

There is no "favorite Worker" or "direct invite" mechanism. If a Requester has had an
excellent experience with a Worker, the next time they post a job, they must browse the
ranked list and hope the Worker appears (and is within range, Available, and not busy).
They can select that Worker from the list, but if three Workers are sent simultaneously
and a different Worker accepts first, the preferred Worker is released.

This is a known product gap rather than a requirements flaw — it is not listed in
Section 6 (Out of Scope) or Section 8 (Phased Rollout). Recommend at minimum adding a
"Direct request" or "Favorite Worker" feature to the future scope list in Section 6,
so it is on record for post-Phase 1 planning.

---

### R6. [Confusion] Commission Disclosure — Is the Platform Fee Included in the Price or Added on Top?

**Location:** FR-REQ-08, C.3 §2

C.3 §2 states that the 15% commission "is not an additional charge — it is included in
the agreed price." FR-REQ-08 says the "full job price and the platform's escrow process
shall be clearly explained to the Requester before they submit the request. No surprise
charges."

But the Worker sets their price tiers (FR-WORK-02) without any mention of whether their
quoted price is before or after commission. If a Worker sets their Tier 1 at $50, does
the Requester pay $50 (Worker receives $42.50) or $57.50 (Worker receives $50)? The
current model is: Requester pays $50, Worker receives $42.50, App Owner retains $7.50.
This is consistent with C.3 §2 — the commission comes out of the stated price. But the
requirements never explicitly tell Workers this is how their pricing tiers work. A Worker
who thinks their $50 tier means they receive $50 will be surprised.

Recommend: explicitly state in FR-WORK-02 that the Worker's stated tier price is the
gross amount the Requester pays, from which the 15% platform commission is deducted
before payout. This should also be stated clearly in the Worker onboarding module
(Phase 2, FR-WORK-07).

---

### R7. [Gap] Ice Treatment Cannot Be Formally Included in Job Scope

**Location:** FR-REQ-01, FR-SVC-03

FR-SVC-03 allows ice treatment to be added to scope "if the Requester specifies it in
the job description and the Worker explicitly agrees." But the job posting form (FR-REQ-01)
has only a free-text notes field — there is no structured "ice treatment required"
checkbox or toggle. A Requester who types "please also salt the driveway" in notes might
have this missed by a Worker who reads the job posting quickly. And there is no mechanism
for the Worker to "explicitly agree" to ice treatment before acceptance — agreement is
inferred from acceptance with the notes visible.

Recommend adding a formal job scope field for ice treatment (checkbox: "Ice treatment
required — Worker must agree to this scope") with a separate Worker acknowledgement at
acceptance. This prevents disputes about whether ice treatment was agreed.

---

### R8. [Gap] Property Damage Discovery After 24-Hour Window

**Location:** FR-ASSUR-02, FR-DISP-01, FR-DMG-03

Property damage disputes must be raised within 24 hours of job completion. Some property
damage from snow clearing operations may not be visible for days after the snow melts:
irrigation heads snapped under snow, cracked concrete that reveals itself in spring,
damaged driveway sealant. A Requester who discovers damage in March from a January job
has no recourse.

The 24-hour window is designed to prevent abuse (Requesters manufacturing claims), but
it may be too strict for latent physical damage. This is a genuine consumer protection
gap. The requirements should at minimum acknowledge the limitation explicitly, and
consider whether a 7-day window for property damage (vs. 2 hours for quality) better
balances the competing interests. This is a decision for the product owner.

---

### R9. [Gap] 30-Minute Escrow Window — Evening Job Posts

**Location:** FR-JOB-02, FR-JOB-01

If a Requester posts a job at 11:45 PM (before a predicted overnight snowfall) and a
Worker accepts at 11:50 PM, the Requester has until 12:20 AM to deposit the escrow. If
the Requester is asleep, the job is auto-cancelled, the Worker is notified, and the
Requester wakes up to find the request was cancelled and will need to post again (likely
to a smaller available Worker pool in the morning). This is an awkward user experience
for what is arguably the most common SnowReach scenario: overnight snow that needs
clearing first thing in the morning.

Consider whether the escrow window should be extended (or paused) during late-night hours,
or whether the job posting flow should warn Requesters to remain available when posting
late at night. Alternatively, the job posting could have a "scheduled for morning"
option that holds the request until a defined time before the desired start.

---

### R10. [Confusion] Requester Cancellation Fee at Minimum Job Price

**Location:** FR-JOB-09, FR-PAY-04, BR-12, BR-16

The $10 cancellation fee applies to cancellations after escrow confirmation but before
In Progress. The minimum job price is $20 (BR-16). If a Requester cancels a minimum-
price ($20) job after confirming, they receive a $10 refund and the App Owner retains
$10 (50% of the escrow). This may feel disproportionate for a small job. The requirements
should acknowledge this edge case and confirm it is the intended outcome.

---

## Cross-Cutting Issues

*Items that affect more than one perspective or the platform as a whole.*

---

### X1. [Legal] PIPEDA Compliance Requirements Are Absent

**Location:** Section 3.14, Appendix B

The Privacy Policy requirements (FR-LEGAL-02, FR-LEGAL-03) specify some data handling
commitments (address privacy, contact info not shared) but do not address the platform's
obligations under the *Personal Information Protection and Electronic Documents Act*
(PIPEDA), which governs commercial organizations collecting personal information from
Canadians.

Missing from the requirements:
- A user's right to access their personal data held by the platform
- A user's right to request correction of inaccurate data
- A user's right to request deletion of their data upon account closure
- Disclosure of the data transferred to US-based processors (Firebase, Stripe) and the
  implications under PIPEDA's cross-border transfer provisions
- Breach notification obligations (72 hours to the Privacy Commissioner for material
  breaches)

These are not optional — they are legal obligations for commercial platforms operating
in Canada. Recommend adding a Section 3.23 PIPEDA Compliance Requirements or expanding
FR-LEGAL-02 and FR-LEGAL-03 to capture these obligations. This almost certainly needs
legal review before the Privacy Policy is drafted.

---

### X2. [Legal] HST/GST Obligations for Workers Are Unaddressed

**Location:** Appendix A.2

Appendix A.2 §3 states that Workers are responsible for "HST/GST registration and
collection where applicable." This is a one-line acknowledgement of a significant
practical gap:

- In Ontario, an individual providing taxable services with annual revenue over $30,000
  is required to register for HST and collect 13% from customers.
- A Worker who completes 30 jobs at $100 each has earned $3,000 from the platform —
  but over a winter career, a productive Worker could easily exceed $30,000.
- If a Worker is HST-registered, the Requester owes 13% HST on top of the job price.
  But the platform has no mechanism to add HST to the Requester's payment.

The requirements need to address this at the policy level: either (a) the platform
assumes all Worker prices are inclusive of applicable taxes and Workers are responsible
for remitting from their 85%, or (b) the platform adds a tax field to the job posting
for HST-registered Workers, or (c) the platform explicitly notes that tax obligations
are outside platform scope and Workers must factor tax into their pricing.

Option (a) is the simplest for v1. Whichever is chosen, the decision should be explicit.

---

### X3. [Legal] Referral Fraud Flag Is Missing

**Location:** Section 9.7, Section 3.22

FR-BOOT-21 defines referral fraud detection (same device, IP, payment method). But
Section 3.22 (the flag registry) has no referral fraud flag. A flag — say, RF-08 or a
new Fraud flag category — should be added: "Referral relationship flagged for suspected
fraud (shared device/IP/payment)." Without a flag, referral fraud detection produces no
Admin alert and leaves the detection entirely to FR-BOOT-21's passive logic. Referral
abuse is a known risk in marketplace platforms and deserves an active monitoring flag.

---

### X4. [Gap] Contact Information Sharing in Messaging Is Policy-Only

**Location:** FR-MSG-03

FR-MSG-03 prohibits sharing contact information in messages and requires "a persistent
notice" in the thread. But there is no technical enforcement — no automated detection
of phone numbers or emails in message content. Many marketplace platforms automatically
scrub or flag messages containing contact information patterns (phone number formats,
email addresses). Without technical enforcement, this is an unenforceable policy. Workers
and Requesters who find each other's contact information via SnowReach could then
transact off-platform, depriving the App Owner of commission.

This is both a policy enforcement gap and a revenue protection issue. Recommend the
specification phase evaluate whether automated pattern detection for contact information
in messages is feasible.

---

### X5. [Gap] Account Deletion / Right to Be Forgotten Is Not Addressed

**Location:** Section 3.14 generally

There is no requirement anywhere in the document for users to be able to close their
account or request deletion of their personal data. Under PIPEDA (see X1 above), users
have the right to request deletion of personal information, subject to the platform's
retention obligations (7-year records for job, dispute, and financial records).

The platform needs a defined account closure process: what data is deleted, what is
retained (and for how long, and why), and how the user is notified. This intersects with
the 7-year immutable audit log requirement (FR-LOG-07) — audit records cannot be deleted,
but the user's personal profile data can potentially be anonymized.

---

### X6. [Gap] Operating Hours Are Not Enforced by the Platform

**Location:** FR-SVC-07, D.6

The requirement that motorized equipment not be operated before 07:00 or after 23:00 is
a service standard — but the platform has no mechanism to enforce it. Nothing prevents
a Worker from tapping "Mark In Progress" at 05:00 AM. This should either be a technical
enforcement (block the In Progress button outside permitted hours, with a warning) or
explicitly documented as a policy-only obligation with no technical enforcement, so
developers and testers know not to implement it as a gating control.

---

### X7. [Confusion] Commission Exclusion on Referral/Early Adopter Jobs — Phase 1 Mechanics

**Location:** FR-BOOT-12, FR-BOOT-15, Section 8 Phase 1

In Phase 1, Early Adopter Workers receive 0% commission on their first 10 jobs. The
Phase 1 payment model charges the Requester after completion. The Early Adopter Worker
receives "100% of the agreed price" for those jobs. But simultaneously, a first-time
Requester also receives 0% commission on their first completed job (FR-BOOT-15), meaning
the Worker receives 100% of the agreed price for that job too.

What if an Early Adopter Worker completes their first job for a first-time Requester?
The Worker's Job 1 is 0% commission; the Requester's first job is also 0% commission.
Both incentives apply to the same job. Is the Worker payout 100% in this case? (Yes —
same result whether one or both incentives apply; the App Owner absorbs the loss.) This
should be stated explicitly to avoid developer confusion about whether two different
commission calculations need to be combined.

---

## Summary — Priority Assessment

The following items represent the highest priority before the requirements are approved:

| # | Item | Type | Priority |
|---|------|------|----------|
| A3 | Fraud detection thresholds inconsistent (15% vs. 20%, 25% vs. 30%) | Conflict | **High** |
| A4 | BR-02 superseded ranking rule still in document | Conflict | **High** |
| W4 | Insurance declaration phase conflict (FR-WORK-08 vs. BR-18) | Conflict | **High** |
| W1 | Phase 1 Worker financial exposure — no protection if charge fails | Legal | **High** |
| W2 | Dispatcher Worker WSIB/sub-contractor personal injury gap | Legal | **High** |
| A2 | No Admin accountability / conflict-of-interest mechanism | Legal | **High** |
| X1 | PIPEDA compliance requirements entirely absent | Legal | **High** |
| X2 | HST/GST obligation for Workers undefined | Legal | **High** |
| A1 | FR-AUTHZ-02 vs. FR-DISP-16 internal contradiction | Conflict | **Medium** |
| R2 | Requester has no recourse when undisclosed person appears In Progress | Gap | **Medium** |
| W3 | "Adequate insurance" undefined — no minimum coverage amount | Legal | **Medium** |
| R1 | 2-hour dispute window is very short for working Requesters | Gap | **Medium** |
| A5 | No consequence for failed "Return to complete" ruling | Gap | **Medium** |
| A6 | No response deadline for "Increased payment" ruling | Gap | **Medium** |
| W6/R3 | Start time is not a formal job posting field | Gap | **Medium** |
| A9 | Section numbers are out of order in the document | Confusion | **Low** |
| X5 | No account deletion / PIPEDA right to erasure process | Gap | **Low** |
| X4 | Contact info sharing in messages is policy-only, not enforced | Gap | **Low** |
| R4 | Completion photos are optional | Gap | **Low** |
| X6 | Operating hours not technically enforced | Confusion | **Low** |

---

*This document is a review artifact only. It does not modify REQUIREMENTS.md. All items
require user decision before any changes are made to the requirements.*
