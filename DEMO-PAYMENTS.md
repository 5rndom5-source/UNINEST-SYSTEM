# Payment presentation walkthrough

This is a simulation, not a payment gateway or an NSFAS integration. It handles the initial deposit and first month's rent only. No real payment/card information or funding documents are accepted. Real funding rules, provider eligibility, monthly invoicing, refunds, and reconciliation remain separate production work.

## Setup

Apply the additive Database/migrations/002-demo-funding.sql migration to an existing database. Do not run the destructive base schema against existing data. Start the local preview using tools/start-upgrade-preview.ps1 -DemoPayments. Production should leave UNINEST_DEMO_PAYMENTS unset or false. Both student and landlord navigation then display Demo payments.

## Self-funded example

1. Student requests a booking; owning landlord accepts it.
2. Landlord opens Demo payments and proposes the deposit. First-month rent is snapshotted from the room price. These demo terms are proposed after booking acceptance, not automatically charged or presented as previously agreed terms.
3. Student opens Demo payments, chooses Self-funded, reviews the separate deposit/rent amounts and accepts them.
4. Run Declined, then retry with Success. The page displays a clearly labelled demo receipt. Repeated completion requests do not create another payment or ledger credit.
5. Landlord sees the same result; both receive in-app notifications. Email delivery is not part of this simulation.

## Bursary / NSFAS example

1. Use a separate accepted booking with a deposit/rent proposal.
2. Student chooses NSFAS or Bursary (provider name required for Bursary).
3. Until verification, checkout is unavailable. Landlord records a simulated approval or rejection and a fictional reference, with separate deposit/rent coverage. Coverage cannot exceed either charge.
4. Student reviews and accepts the resulting split. A changed split invalidates prior acceptance; stale forms are rejected.
5. Student simulates payment of only the uncovered amount. Fully funded students have no payment button.
6. Landlord records a simulated external provider receipt with a fictional reference. Verification alone never creates this receipt or a paid status.
7. Student and provider payments are separately recorded in payments and transaction_history with DEMO-V2 prefixes, and displayed as demo receipts to both parties.

Completed payments cannot be reverted by this workflow or edited through the general admin payment endpoint. Funding terms cannot change after either party's successful payment. Failed/cancelled attempts remain unpaid and can be retried; they do not create tenancies or paid receipts. Refunds/corrections require a future explicit workflow. Old completed DEMO-BOOKING records cannot be charged again using this new flow.

## Checks

Run tools/check-upgrades.ps1 and tools/check-database-upgrades.ps1. For browser journeys, start tools/start-funding-test-preview.ps1 against the fresh disposable database emitted by the database check, then run tools/check-funding-browser.cjs with Playwright and Edge. This isolated server uses port 8092 and synthetic accounts only. Stop that test server after verification. Never deploy the test fixtures as production accounts.
