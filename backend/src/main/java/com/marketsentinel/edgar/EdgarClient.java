package com.marketsentinel.edgar;

import com.marketsentinel.config.SentinelProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Talks to SEC EDGAR, the SEC's free public filing database. The SEC asks every client to send a
 * User-Agent with a name and contact email, and to stay under 10 requests per second; this client
 * waits 125 ms between requests.
 */
@Component
public class EdgarClient {

    private static final long MIN_GAP_MS = 125;

    private final RestClient http;
    private final ObjectMapper json;
    private final String dataUrl;
    private final String archivesUrl;
    private volatile Map<String, Company> tickers;
    private long lastRequestAt;

    public record Company(String cik, String ticker, String name) {
    }

    public record Filing(String accession, String form, String filingDate, String primaryDocument, String items) {
    }

    public EdgarClient(SentinelProperties props, ObjectMapper json) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, props.sec().userAgent())
                .build();
        this.json = json;
        this.dataUrl = props.sec().baseUrl();
        this.archivesUrl = props.sec().archivesUrl();
    }

    public Optional<Company> company(String ticker) {
        if (tickers == null) {
            Map<String, Company> map = new HashMap<>();
            JsonNode root = json.readTree(get(archivesUrl + "/files/company_tickers.json"));
            for (JsonNode n : root) {
                String cik = String.format("%010d", n.get("cik_str").asLong());
                String t = n.get("ticker").asString().toUpperCase(Locale.ROOT);
                map.putIfAbsent(t, new Company(cik, t, n.get("title").asString()));
            }
            tickers = map;
        }
        return Optional.ofNullable(tickers.get(ticker.toUpperCase(Locale.ROOT)));
    }

    /** The company's most recent filings (EDGAR returns up to about 1,000 in "recent"). */
    public List<Filing> recentFilings(String cik) {
        JsonNode recent = json.readTree(get(dataUrl + "/submissions/CIK" + cik + ".json")).path("filings").path("recent");
        JsonNode acc = recent.path("accessionNumber");
        JsonNode form = recent.path("form");
        JsonNode date = recent.path("filingDate");
        JsonNode doc = recent.path("primaryDocument");
        JsonNode items = recent.path("items");
        return java.util.stream.IntStream.range(0, acc.size())
                .mapToObj(i -> new Filing(acc.get(i).asString(), form.get(i).asString(), date.get(i).asString(),
                        doc.get(i).asString(), items.has(i) ? items.get(i).asString() : ""))
                .toList();
    }

    /** The raw Form 4 XML. EDGAR's primaryDocument often points at an HTML rendering under "xslF345X0x/". */
    public String form4Xml(String cik, Filing filing) {
        String document = filing.primaryDocument().replaceFirst("^xsl[^/]*/", "");
        String folder = filing.accession().replace("-", "");
        return get(archivesUrl + "/Archives/edgar/data/" + Long.parseLong(cik) + "/" + folder + "/" + document);
    }

    private synchronized String get(String url) {
        long wait = lastRequestAt + MIN_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while rate limiting", e);
            }
        }
        try {
            return http.get().uri(url).retrieve().body(String.class);
        } finally {
            lastRequestAt = System.currentTimeMillis();
        }
    }
}
