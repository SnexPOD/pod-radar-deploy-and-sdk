import io.podradar.sdk.PodRadarClient;
import io.podradar.sdk.model.ImageRef;
import io.podradar.sdk.model.SearchRequest;
import io.podradar.sdk.model.SearchResponse;

import java.io.File;
import java.net.URI;

public final class ImageSearchDemo {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage:");
            System.err.println("  java ImageSearchDemo file <query.jpg> [k]");
            System.err.println("  java ImageSearchDemo url <https://example.com/query.jpg> [k]");
            System.err.println("  java ImageSearchDemo text <query text...>");
            System.exit(2);
        }

        String endpoint = requireEnv("POD_RADAR_ENDPOINT");
        String apiKey = requireEnv("POD_RADAR_API_KEY");
        String mode = args[0];
        int k = args.length >= 3 && !mode.equals("text") ? Integer.parseInt(args[2]) : 24;

        try (PodRadarClient client = PodRadarClient.builder()
                .endpoint(endpoint)
                .apiKey(apiKey)
                .build()) {
            SearchRequest req;
            if ("file".equals(mode)) {
                req = SearchRequest.fromFile(new File(args[1]), k);
            } else if ("url".equals(mode)) {
                req = SearchRequest.fromUrl(URI.create(args[1]), k);
            } else if ("text".equals(mode)) {
                StringBuilder text = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; i++) text.append(' ').append(args[i]);
                req = SearchRequest.fromText(text.toString(), 24);
            } else {
                throw new IllegalArgumentException("unknown mode: " + mode);
            }

            SearchResponse r = client.search(req);
            System.out.printf("model=%s k=%d hits=%d%n", r.model(), r.k(), r.results().size());
            for (ImageRef hit : r.results()) {
                System.out.printf("%d %.4f %s %s%n",
                        hit.imageId(), hit.score(), nvl(hit.title()), nvl(hit.full()));
            }
        }
    }

    private static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.trim().isEmpty()) throw new IllegalStateException("missing env: " + name);
        return v;
    }

    private static String nvl(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
