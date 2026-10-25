package com.marketsentinel.simulator;

import com.marketsentinel.market.AccountType;
import com.marketsentinel.market.Side;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates a realistic trading day for 20 simulated tickers, with five schemes hidden in it:
 * wash trading (AURA), spoofing (BRISK), marking the close (CIRRO), pump and dump (EMBER) and
 * front-running (FALCO). Same date + seed = same day, so demos and tests are repeatable.
 *
 * The tickers are made-up names for simulation; they do not refer to real companies.
 */
public class MarketSimulator {

    public static final List<String> SYMBOLS = List.of("AURA", "BRISK", "CIRRO", "DYNAMO", "EMBER", "FALCO", "GLINT",
            "HELIX", "IRIS", "JUNO", "KESTREL", "LUMA", "MERIDIAN", "NOVA", "ORBIT", "PRISM", "QUILL", "RIVET", "SOLACE",
            "TALON");

    private static final int CLIENTS = 60;
    private static final int TRADES_PER_SYMBOL = 120;

    private final ZoneId zone;

    public MarketSimulator(ZoneId zone) {
        this.zone = zone;
    }

    public record Injected(String pattern, String symbol, String accounts) {
    }

    public record Result(SimulatedDay day, List<Injected> injected) {
    }

    public Result generate(LocalDate date, long seed) {
        SimulatedDay day = new SimulatedDay(date, zone, seed);
        for (int i = 1; i <= CLIENTS; i++) {
            String ref = String.format("C%03d", i);
            day.account(ref, ref, AccountType.CLIENT);
        }
        for (int i = 1; i <= 3; i++) {
            day.account("EMP-0" + i, "EMP-0" + i, AccountType.EMPLOYEE);
        }
        day.account("PROP-01", "FIRM", AccountType.PROPRIETARY);

        for (String symbol : SYMBOLS) {
            if (!symbol.equals("EMBER")) {
                normalFlow(day, symbol, symbol.equals("CIRRO") ? day.at(15, 49, 59) : day.at(15, 59, 50));
            }
        }
        List<Injected> injected = new ArrayList<>();
        injected.add(washTrading(day));
        injected.add(spoofing(day));
        injected.add(markingTheClose(day));
        injected.add(pumpAndDump(day));
        injected.add(frontRunning(day));
        day.trades.sort(Comparator.comparing(SimulatedDay.TradeRow::executedAt));
        return new Result(day, injected);
    }

    /** Ordinary trading: a gentle random walk, random client pairs, a few slow normal cancels. */
    private void normalFlow(SimulatedDay day, String symbol, Instant lastTime) {
        double price = 10 + day.random.nextInt(190) + day.random.nextInt(100) / 100.0;
        long open = day.at(9, 30, 5).getEpochSecond();
        long span = lastTime.getEpochSecond() - open;
        List<Long> times = new ArrayList<>();
        for (int i = 0; i < TRADES_PER_SYMBOL; i++) {
            times.add(open + (long) (day.random.nextDouble() * span));
        }
        times.sort(Long::compare);
        for (long t : times) {
            price = Math.max(1, price * (1 + day.random.nextGaussian() * 0.001));
            String[] pair = clientPair(day);
            long qty = 100L * (1 + day.random.nextInt(10));
            day.trade(symbol, pair[0], pair[1], SimulatedDay.price(price), qty, Instant.ofEpochSecond(t));
        }
        for (int i = 0; i < 8; i++) {
            Instant placed = Instant.ofEpochSecond(open + (long) (day.random.nextDouble() * (span - 3600)));
            day.cancelledOrder(client(day), symbol, day.random.nextBoolean() ? Side.BUY : Side.SELL,
                    100L * (1 + day.random.nextInt(10)), SimulatedDay.price(price * 0.98), placed,
                    placed.plusSeconds(60 + day.random.nextInt(1200)));
        }
    }

    /** Three accounts with one owner trade AURA back and forth: volume without change of ownership. */
    private Injected washTrading(SimulatedDay day) {
        String[] ring = { "W1A", "W1B", "W1C" };
        for (String a : ring) {
            day.account(a, "OWNER-W1", AccountType.CLIENT);
        }
        double price = lastPrice(day, "AURA");
        for (int i = 0; i < 14; i++) {
            String buyer = ring[i % 3];
            String seller = ring[(i + 1) % 3];
            day.trade("AURA", buyer, seller, SimulatedDay.price(price * (1 + day.random.nextGaussian() * 0.0005)),
                    2000 + 100L * day.random.nextInt(20), day.at(11, 0, 0).plusSeconds(i * 300L + day.random.nextInt(60)));
        }
        return new Injected("WASH_TRADING", "AURA", "W1A, W1B, W1C (owner OWNER-W1)");
    }

    /** SPF-1 flashes five huge BUY orders, cancels each within seconds, and sells into the bid it created. */
    private Injected spoofing(SimulatedDay day) {
        day.account("SPF-1", "SPF-1", AccountType.CLIENT);
        double price = lastPrice(day, "BRISK");
        Instant start = day.at(13, 0, 0);
        for (int i = 0; i < 5; i++) {
            Instant placed = start.plusSeconds(i * 20L);
            day.cancelledOrder("SPF-1", "BRISK", Side.BUY, 40_000, SimulatedDay.price(price * 0.998), placed,
                    placed.plusSeconds(2 + day.random.nextInt(5)));
        }
        for (int i = 0; i < 3; i++) {
            day.trade("BRISK", client(day), "SPF-1", SimulatedDay.price(price * 1.005), 1500,
                    start.plusSeconds(30 + i * 35L));
        }
        return new Injected("SPOOFING", "BRISK", "SPF-1");
    }

    /** MKC-1 barely trades all day, then buys heavily in the last 10 minutes and lifts the close ~3 %. */
    private Injected markingTheClose(SimulatedDay day) {
        day.account("MKC-1", "MKC-1", AccountType.CLIENT);
        double price = lastPrice(day, "CIRRO");
        day.trade("CIRRO", "MKC-1", client(day), SimulatedDay.price(price), 200, day.at(10, 15, 0));
        for (int i = 0; i < 8; i++) {
            double p = price * (1 + 0.004 * (i + 1));
            day.trade("CIRRO", "MKC-1", client(day), SimulatedDay.price(p), 3000, day.at(15, 51, 0).plusSeconds(i * 65L));
            if (i % 3 == 1) {
                String[] pair = clientPair(day);
                day.trade("CIRRO", pair[0], pair[1], SimulatedDay.price(p), 300, day.at(15, 51, 30).plusSeconds(i * 65L));
            }
        }
        return new Injected("MARKING_THE_CLOSE", "CIRRO", "MKC-1");
    }

    /** PD-1 and PD-2 accumulate EMBER at $4, the price is hyped to ~$5.80, and they sell near the top. */
    private Injected pumpAndDump(SimulatedDay day) {
        day.account("PD-1", "PD-1", AccountType.CLIENT);
        day.account("PD-2", "PD-2", AccountType.CLIENT);
        // 1. Quiet morning around $4.00, while PD-1 and PD-2 accumulate.
        for (int i = 0; i < 30; i++) {
            String[] pair = clientPair(day);
            day.trade("EMBER", pair[0], pair[1], SimulatedDay.price(4.0 * (1 + day.random.nextGaussian() * 0.005)),
                    500L + 100L * day.random.nextInt(10), day.at(9, 31, 0).plusSeconds(i * 150L));
        }
        for (int i = 0; i < 6; i++) {
            day.trade("EMBER", i % 2 == 0 ? "PD-1" : "PD-2", client(day), SimulatedDay.price(4.0 + 0.01 * i), 6_000,
                    day.at(9, 45, 0).plusSeconds(i * 400L));
        }
        // 2. The hype: retail buying pushes the price up 45 % by 13:15.
        int rampTrades = 60;
        long rampStart = day.at(10, 45, 0).getEpochSecond();
        long rampEnd = day.at(13, 15, 0).getEpochSecond();
        for (int i = 0; i < rampTrades; i++) {
            double progress = (i + 1) / (double) rampTrades;
            String[] pair = clientPair(day);
            day.trade("EMBER", pair[0], pair[1], SimulatedDay.price(4.0 + 1.80 * progress), 800L + 100L * day.random.nextInt(15),
                    Instant.ofEpochSecond(rampStart + (long) ((rampEnd - rampStart) * progress)));
        }
        // 3. The dump: PD-1 and PD-2 sell 36,000 shares near the top.
        for (int i = 0; i < 6; i++) {
            day.trade("EMBER", client(day), i % 2 == 0 ? "PD-1" : "PD-2", SimulatedDay.price(5.60 + 0.03 * i), 6_000,
                    day.at(13, 0, 0).plusSeconds(i * 400L));
        }
        // 4. The fall: latecomers are left holding shares as the price slides to ~$4.60.
        for (int i = 0; i < 30; i++) {
            String[] pair = clientPair(day);
            day.trade("EMBER", pair[0], pair[1], SimulatedDay.price(5.6 - 1.0 * (i + 1) / 30.0), 400L + 100L * day.random.nextInt(8),
                    day.at(13, 45, 0).plusSeconds(i * 270L));
        }
        return new Injected("PUMP_AND_DUMP", "EMBER", "PD-1, PD-2");
    }

    /** Employee EMP-02 buys FALCO 40 s before a 25,000-share client buy order, then sells after it. */
    private Injected frontRunning(SimulatedDay day) {
        day.account("CL-BIG", "CL-BIG", AccountType.CLIENT);
        double price = lastPrice(day, "FALCO");
        day.trade("FALCO", "EMP-02", client(day), SimulatedDay.price(price), 2_000, day.at(14, 29, 20));
        Instant placed = day.at(14, 30, 0);
        String big = day.bigOrder("CL-BIG", "FALCO", Side.BUY, 25_000, SimulatedDay.price(price * 1.02), placed);
        for (int i = 0; i < 5; i++) {
            day.fillAgainst("FALCO", big, "CL-BIG", Side.BUY, client(day), SimulatedDay.price(price * (1 + 0.002 * (i + 1))),
                    5_000, placed.plusSeconds(5 + i * 40L));
        }
        day.trade("FALCO", client(day), "EMP-02", SimulatedDay.price(price * 1.012), 2_000, day.at(14, 50, 0));
        return new Injected("FRONT_RUNNING", "FALCO", "EMP-02 ahead of CL-BIG");
    }

    private static double lastPrice(SimulatedDay day, String symbol) {
        return day.trades.stream().filter(t -> t.symbol().equals(symbol))
                .max(Comparator.comparing(SimulatedDay.TradeRow::executedAt))
                .map(t -> t.price().doubleValue()).orElse(50.0);
    }

    private static String client(SimulatedDay day) {
        return String.format("C%03d", 1 + day.random.nextInt(CLIENTS));
    }

    private static String[] clientPair(SimulatedDay day) {
        String a = client(day);
        String b;
        do {
            b = client(day);
        } while (b.equals(a));
        return new String[] { a, b };
    }
}
