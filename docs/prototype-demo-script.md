# YoSnowMow — Prototype Demo Script
**Phase 0 UI Prototype · Presenter Guide**

> **Total time:** ~25 minutes + Q&A
> **Prototype URL:** [https://perelgut.github.io/YoSnowMow/](https://perelgut.github.io/YoSnowMow/)
> **Best viewed on:** Desktop (1280px+) or mobile device

---

## Before You Start

**Setup checklist:**
- [ ] Open prototype in browser, confirm it loads
- [ ] The "Demo Mode" panel is visible in the bottom-right corner (dev build only)
- [ ] Have the stakeholder review template ready to hand out after the demo
- [ ] Silence notifications on presenting device
- [ ] If screen-sharing, zoom browser to 125% for readability

**Key disclaimer to read aloud:**

> *"What you're about to see is a Phase 0 UI prototype. Every screen is fully interactive, but all data is mock — nothing is saved, no payments are processed, and no emails are sent. The goal today is to validate the user experience and get your feedback before we build the real backend. Think of it as a clickable wireframe with real design."*

---

## Part 1 — Introduction *(2 minutes)*

**Say:**
> *"YoSnowMow connects Ontario homeowners who need snow clearing or lawn work with nearby neighbours who own snowblowers or equipment. Think Uber, but for your driveway — and the driver lives two streets away."*

**Key points to hit:**
- Two user types: **Requesters** (property owners) and **Workers** (equipment owners)
- Funds held in **escrow** until job is confirmed complete — neither party risks getting burned
- **15% platform fee** on the worker payout; HST collected and passed through
- Phase 0 is prototype only — backend (Firebase + Stripe) is Phase 1

**Show:** The home page hero section. Point out the tagline and "Post a Job" CTA.

---

## Part 2 — Requester Flow *(8 minutes)*

> *"Let's walk through the experience as a homeowner who needs their driveway cleared."*

### Step 1 — Post a Job *(3 min)*

1. Click **Post a Job** on the home page
2. **Step 1 — Location:** Type any address, click Next. Point out:
   - 1-second "searching for workers" animation
   - *"In Phase 1, this calls Google Maps Geocoding to find real workers in range."*
3. **Step 2 — Services:** Check Driveway + Steps, pick sizes. Point out:
   - Live price total updating as you select
   - *"Pricing is transparent upfront — no surprises after the job."*
4. **Step 3 — Schedule:** Select ASAP. Mention the scheduled option exists too.
5. **Step 4 — Review:** Walk through the price breakdown:
   - *"The customer sees exactly what the worker earns vs. the platform fee. Full transparency."*
   - Check the acknowledgement box. Click **Post Job**.

**Talking point — ease of posting:**
> *"We designed the wizard to take under 2 minutes. The 4-step structure was intentional — we validated it breaks the task into digestible chunks without overwhelming the user."*

---

### Step 2 — Job Status Tracking *(2 min)*

After posting, you land on the Job Status page. Walk through:

1. **Timeline** — show the 5-step timeline (Job Posted → Awaiting Payment → etc.)
2. **Dev Tools** card (bottom) — click **Advance** a couple of times to move the job to CONFIRMED, then IN_PROGRESS
3. Once CONFIRMED: the **Worker card** appears. Click **View Profile** to show the modal.
4. Advance to COMPLETE. Show the **Rate Worker** and **Raise Dispute** buttons appear.

**Talking point — trust and safety:**
> *"The 2-hour dispute window after completion is the key safety feature for requesters. If the work isn't done right, they have a window to raise a dispute before funds are released."*

---

### Step 3 — Rate and Release *(2 min)*

Click **Rate Your Worker**:
1. Show the interactive 5-star widget (hover effect)
2. Show the Worker's mock rating of the requester (read-only)
3. Click **Submit Rating & Release Payment**
4. Show the confirmation screen with payout breakdown

**Talking point — mutual rating:**
> *"Both parties rate each other. This creates accountability on both sides — requesters keep their property accessible and respectful, workers maintain quality. Bad actors lose access over time."*

---

## Part 3 — Worker Flow *(6 minutes)*

> *"Now let's switch to the Worker perspective — someone who owns a snowblower and wants to earn money in their neighbourhood."*

**Switch roles:** Click **❄️ Worker** in the Demo Mode panel.

### Step 1 — Registration *(2 min)*

Navigate to `/worker/register` (or tap **Register** in the nav):

1. **Step 1 — Personal Info:** Point out 18+ age validation and contractor acknowledgement
   - *"Workers are independent contractors, not employees. The acknowledgement is legally important."*
2. **Step 2 — Equipment:** Show the photo upload mock, size/type selects
3. **Step 3 — Service Area:** Show the radius slider and estimated properties count
   - *"The property estimate helps workers understand their potential market before committing."*
4. **Step 4 — Payment:** Show the payout explainer card, click **Connect with Stripe** (2s mock delay)
   - *"In Phase 1 this triggers Stripe Connect Express onboarding. Workers get paid directly to their bank."*

---

### Step 2 — Incoming Job Request *(2 min)*

Navigate to **/worker/job-request**. If a job is in REQUESTED state:

1. Show the **10-minute countdown timer** — it counts down in real time
2. Point out the **partial address** (privacy until accepted), earnings breakdown
3. When `< 2 minutes` remain, show the timer and card border turning **red**
4. Click **✓ Accept Job** — show the confirmed state and automatic redirect

**Talking point — worker experience:**
> *"Workers respond to one request at a time. Sequential dispatch ensures fair opportunity and prevents over-commitment. The 10-minute window was chosen based on how long it realistically takes to get ready to go."*

---

### Step 3 — Active Job & Earnings *(2 min)*

1. On Active Job: show the **Check In** button → IN_PROGRESS state
2. Show the **photo upload** gate — Mark Complete is disabled until a photo is added
   - *"Photo documentation is dispute protection for workers — they have proof the job was done."*
3. Show the **Earnings dashboard** — stats, expandable job history with billing breakdown

---

## Part 4 — Admin Flow *(4 minutes)*

> *"Finally, the platform operator view."*

**Switch roles:** Click **⚙️ Admin** in the Demo Mode panel.

### Dashboard *(1.5 min)*

1. Show the **4 stat cards** with coloured borders
2. Show the **Recent Activity feed** (right sidebar on desktop)
3. Click a job row in the **Jobs tab** → navigates to Job Detail

**Talking point — oversight:**
> *"The admin can see everything in real time. In Phase 1, this dashboard will have live data from Firestore with WebSocket-like updates."*

---

### Job Detail + Dispute Resolution *(2.5 min)*

On the Job Detail page:

1. Walk through **financials** and **parties** cards
2. Show **Admin Actions** — override status dropdown, Force Release, Issue Refund (all have confirmation modals — click one)
3. Navigate back to a DISPUTED job (use Dev Tools on the Requester job status page to raise a dispute, then view in admin)
4. Show the **dispute section**: requester statement, worker statement, evidence photos
5. Select **Split** resolution — show the percentage slider with live payout preview
6. Click **Resolve Dispute** → confirmation modal

**Talking point — adjudication:**
> *"Admins have three options: Release (favour worker), Refund (favour requester), or Split. The split slider lets admins apply proportional remedies — e.g., 70/30 if the job was mostly done."*

**Talking point — analytics roadmap:**
> *"The Analytics page is a Phase 1 placeholder. We plan revenue charts, worker utilisation heatmaps, and dispute rate trends."*

---

## Part 5 — Q&A Prompts *(5 minutes)*

Use these questions to draw out useful feedback:

1. **"If you were a homeowner, what would stop you from posting your first job?"**
   *(Listening for: trust concerns, price sensitivity, unclear UX steps)*

2. **"If you owned a snowblower, what would you need to see before signing up as a Worker?"**
   *(Listening for: earnings clarity, insurance concerns, liability, volume expectations)*

3. **"Is the pricing model clear? Does 15% feel fair for what the platform provides?"**
   *(Listening for: willingness to pay, comparisons to competitors)*

4. **"What's the one thing you'd change before launch?"**
   *(Open — captures the highest-priority issue in the reviewer's mind)*

5. **"On a scale of 0–10, how likely are you to use this — as a requester or worker — when it launches?"**
   *(NPS anchor — matches the review template)*

---

## Deployment Instructions

To deploy a fresh preview build:

```bash
# Build the frontend
cd yosnowmow/frontend
npm run build

# Deploy to GitHub Pages (current method — Phase 0)
git add dist   # dist is gitignored; this deploys via Actions on push
git push origin main

# Future: Firebase Hosting preview channel (Phase 1+)
# cd yosnowmow/firebase
# firebase hosting:channel:deploy prototype --expires 7d
# → Generates a preview URL: https://yosnowmow--prototype-xxxx.web.app
```

**Current live prototype:**
`https://perelgut.github.io/YoSnowMow/`

---

## After the Session

1. Hand out (or email) the **Stakeholder Review Template** (`docs/stakeholder-review-template.md`)
2. Set a deadline for feedback — suggested: **5 business days**
3. Triage all responses into three buckets:
   - **P1 scope** — must-have before MVP launch
   - **Post-MVP** — desirable but not blocking
   - **Rejected / won't do** — out of scope, document reasoning in `DECISIONS_LOG.md`
4. Schedule a follow-up call to walk through the triage decisions with stakeholders
