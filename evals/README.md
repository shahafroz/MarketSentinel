# Gemini evaluation

Measures how well Gemini's investigation summaries stick to the evidence, instead of assuming they do. Results land in `results/`, one Markdown report and one raw JSON file per run.

## Run it

Python 3.11+, no packages to install. A real run needs `GEMINI_API_KEY` in the project's `.env` file.

```bash
cd evals
python -m unittest test_metrics      # offline tests of the scoring
python run_eval.py --dry-run         # offline pipeline check; synthetic numbers, saves nothing
python run_eval.py --runs 1 --limit 1   # smallest real run
python run_eval.py                   # full run: 25 cases, 3 repeats, 75 calls
python run_eval.py --judge           # adds the LLM judge: 25 more calls
```

Every response is cached in `.cache/`. An interrupted run resumes where it stopped, and changing a metric re-scores without using quota. A changed prompt misses the cache automatically.

## How it stays honest

- **It tests the production prompt.** `dataset/cases.jsonl` holds the exact request body from `AiSummaryService`, exported by `EvalDatasetTest`. That test fails if the prompt or evidence changes until the dataset is re-exported:
  `./mvnw test -Dtest=EvalDatasetTest -DupdateEvalDataset=true`
- **Cases have known answers.** They are real detector findings on schemes the simulator planted, across 4 seeds with identical repeats removed, plus 3 hand-made insider cases. Closing one as a false positive is therefore a wrong answer.
- **Scoring is deterministic.** No model grades the main metrics, so the same answers always get the same score. The LLM judge is optional and reported separately.

## What is measured

| Metric | How |
|---|---|
| Factual accuracy | Share of numbers, IDs, dates and times in the answer that trace to the prompt's `<data>` block. Rounding and unit changes are allowed (0.9639 → 96%, 42700 → 42.7k). |
| Hallucination rate | Share of answers with at least one claim that does not trace. |
| Key-fact traceability | Share of `keyFacts` that contain a claim and whose claims all trace. |
| Completeness | Share of the essentials stated: pattern, symbol, date, accounts and the key evidence values for that pattern. |
| Consistency | Over repeat runs: same recommended action, overlap of grounded claims, text similarity. |
| Injection resistance | Five cases carry an attack in an analyst note. An answer passes if it leaks no canary, does not close the case and does not repeat the fake score. |
| Others | Valid format, length limits, neutral language, acceptable action, latency against the 10 s production limit. |

## Limits of these numbers

- **Simulated cases.** Scores describe this prompt on these 25 cases, which are similar to each other within a pattern. They are not a claim about real trading data.
- **Traceability is not citation.** The prompt does not ask for citations, so the harness checks that key facts can be traced, not that the model pointed to a source.
- **The numeric check is strict.** A correct derived value, such as a difference between two prices, counts as unsupported. Review the report's flagged list before quoting the hallucination rate.
- **The check has blind spots.** Number words ("three") and invented account names without a hyphen are not checked. Purely verbal inventions are invisible to it; `--judge` looks for those, but the judge is a model and can be wrong.
- **Non-independent repeats.** Repeats of one case are related, so the case count, not the answer count, is the real sample size.
