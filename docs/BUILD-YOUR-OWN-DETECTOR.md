# Build the 7th detector yourself: Concentration ("dominant trader")

Goal: add a detector **you** wrote, so you can say in interviews: *"I extended the engine with a concentration detector: here is how it works, how I tested it, and the false positive I had to fix."*

**What it catches:** one beneficial owner (all their linked accounts together) is on one side or the other of a big share of a stock's trading for the day, for example 40 % or more. Surveillance teams watch this because a dominant trader can push or "corner" a thin stock.

**Rule (you implement it):**
For each symbol, for each owner: `ownerVolume / symbolVolume >= minShare` **and** `ownerTrades >= minTrades`.
- `ownerVolume` = shares the owner bought + shares they sold (count a self-trade only once, see step 4).
- Start with `minShare = 0.40` and `minTrades = 5`.

Time needed: about 2–3 hours. Run tests after every step: `cd backend && ./mvnw test`

---

## Step 1: Add the pattern name (5 min)

File: `backend/src/main/java/com/marketsentinel/alert/Pattern.java`

Add a new enum value, e.g. `CONCENTRATION("Concentration")`.

> Question to understand: why can we add a value without a database migration? (Hint: look at the `pattern` column type in `V1__initial_schema.sql`.)

## Step 2: Add the thresholds to the config (10 min)

1. `backend/src/main/resources/application.yml` → under `sentinel.detection`, add:
   ```yaml
   concentration-min-share: 0.40
   concentration-min-trades: 5
   ```
2. `backend/src/main/java/com/marketsentinel/config/SentinelProperties.java` → add two fields to the `Detection` record: `BigDecimal concentrationMinShare` and `int concentrationMinTrades`.

> Spring turns `concentration-min-share` into `concentrationMinShare` automatically ("relaxed binding").

## Step 3: Write the test FIRST (30 min)

Create `backend/src/test/java/com/marketsentinel/detection/ConcentrationDetectorTest.java`.
Copy the shape of `WashTradingDetectorTest.java` and use the helpers in `TestMarket.java` (`trade(...)`, `day(...)`, `accounts()`).

Write these 4 tests:
1. **Clean:** 10 trades spread over 10 different accounts → no finding.
2. **Obvious:** one account is buyer or seller in 6 of 10 trades with most of the shares → exactly one finding, with that account.
3. **Linked accounts:** two accounts with the same owner (use `accounts().add("X1", "OWNER", AccountType.CLIENT)`) that together pass the threshold, while each alone does not → one finding listing both accounts.
4. **Borderline:** the owner is just under 40 % → no finding.

They won't compile yet. That's fine; that is test-driven development.

## Step 4: Write the detector (45 min)

Create `backend/src/main/java/com/marketsentinel/detection/ConcentrationDetector.java`. Skeleton:

```java
public class ConcentrationDetector implements Detector {

    private final BigDecimal minShare;
    private final int minTrades;

    public ConcentrationDetector(BigDecimal minShare, int minTrades) { /* TODO */ }

    @Override
    public Pattern pattern() { /* TODO */ }

    @Override
    public List<Finding> detect(MarketDay day) {
        List<Finding> findings = new ArrayList<>();
        for (var entry : day.tradesBySymbol().entrySet()) {
            // TODO 1: total shares traded in this symbol today
            // TODO 2: for each trade, add its quantity to the buyer's OWNER and the seller's OWNER
            //         (use day.ownerOf(...)). If buyer and seller have the same owner, count it ONCE.
            //         Hint: a Map<String, Long> and merge(owner, qty, Long::sum)
            // TODO 3: also count trades per owner, and remember which accounts each owner used
            // TODO 4: for each owner, compute the share with Ratios.share(ownerVolume, symbolVolume)
            //         and keep the ones >= minShare with >= minTrades trades
            // TODO 5: build a Finding: pattern, symbol, accounts, score, title, reason, evidence map,
            //         date, window start/end (first and last trade time of that owner)
        }
        return findings;
    }
}
```

Score idea: `40 + percent of volume / 2`, so 40 % → 60 and 80 % → 80. Your choice; just be able to explain it.
Evidence idea: `owner`, `ownerShares`, `symbolVolume`, `shareOfVolume`, `trades`, `accounts`.

Look at `WashTradingDetector.java` for a very similar structure (group by owner, build evidence, build `Finding`).

## Step 5: Turn it on (5 min)

`DetectorConfig.java` → add `new ConcentrationDetector(d.concentrationMinShare(), d.concentrationMinTrades())` to the list.

Run `./mvnw test`. Your 4 tests should pass.

## Step 6: The interesting part, false positives (30 min)

Run the full test suite. `SimulatorDetectionTest` runs ALL detectors on simulated days.
**Check:** does your detector fire on stocks where nothing bad was injected? On which symbols, and why?

- Hint: in the simulator, `EMBER` (pump and dump) and `CIRRO` (marking the close) have accounts with big volume. Is your detector flagging them? Is that right or wrong?
- Decide: raise the threshold? Require more trades? Ignore symbols with very little volume?

**Write down what you found and what you changed. This is your interview story.**

## Step 7: Inject your own scheme into the simulator (30 min)

`backend/src/main/java/com/marketsentinel/simulator/MarketSimulator.java`
1. Copy the shape of `washTrading(day)` into a new method, e.g. `concentration(day)`, on a ticker nobody uses yet (e.g. `GLINT`).
2. Make one account (`DOM-1`) take part in many trades with most of the volume.
3. Add it to the `injected` list in `generate(...)`.
4. In `SimulatorDetectionTest`, assert your pattern is found on `GLINT`.

## Step 8: Show it in the dashboard (10 min)

- `frontend/src/types.ts` → add `'CONCENTRATION'` to the `Pattern` type.
- `frontend/src/pages/AlertsPage.tsx` → add it to the `PATTERNS` filter list.
- Restart the API, run a new market day on the overview page, and open your alert.

## Step 9: Document and commit (10 min)

- Add a row to the detector table in `README.md`.
- Commit: `git add -A && git commit -m "Add concentration detector"`.

---

## When you get stuck

Paste the error or the line you don't understand into the chat. Ask "why" questions, not "write it for me", so the code stays yours.

## Interview questions you should be able to answer afterwards

1. Why do you group by **owner** and not by account?
2. Why count a self-trade only once?
3. Why is the threshold in `application.yml` and not in the code?
4. What false positives did you find, and how did you handle them?
5. Why did you write the tests before the detector?
6. What would you change to detect concentration over a **week** instead of a day?
