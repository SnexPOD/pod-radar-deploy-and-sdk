package io.podradar.examples;

import io.podradar.crawler.CrawlerClient;
import io.podradar.crawler.model.HihumbirdSettings;
import io.podradar.crawler.model.HihumbirdSyncState;
import io.podradar.crawler.model.SettingsResponse;
import io.podradar.sdk.error.PodRadarException;

/**
 * 爬虫 SDK:读取 / 更新 hihumbird settings。
 *
 * <pre>
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerSettingsExample \
 *   -Dexec.args="get"
 *
 * mvn -pl examples exec:java -Dexec.mainClass=io.podradar.examples.CrawlerSettingsExample \
 *   -Dexec.args="update --sync-enabled true --sync-interval 15 --sync-overlap 60 \
 *       --cursor-start-at 2026-06-01T00:00:00.000Z --max-run-span-hours 24 \
 *       --rescan-enabled true --rescan-interval 60 --rescan-max-age-days 30 \
 *       --missing-batch-rescan-enabled true --missing-batch-rescan-interval 90 \
 *       --missing-batch-rescan-max-age-days 30"
 * </pre>
 */
public final class CrawlerSettingsExample {

    public static void main(String[] args) {
        if (args.length < 1) {
            usage();
            System.exit(ExampleSupport.ERR_USAGE);
        }

        try (CrawlerClient crawler = ExampleSupport.crawlerBuilder().build()) {
            switch (args[0]) {
                case "get":
                    print(crawler.getSettings());
                    break;
                case "update":
                    HihumbirdSettings updated = crawler.updateSettings(settingsFromArgs(args));
                    System.out.println("updated settings:");
                    printSettings(updated);
                    break;
                default:
                    usage();
                    System.exit(ExampleSupport.ERR_USAGE);
            }
        } catch (PodRadarException e) {
            ExampleSupport.exitPodRadarException("crawler settings op failed", e);
        }
    }

    private static HihumbirdSettings settingsFromArgs(String[] args) {
        return new HihumbirdSettings(
                boolRequired(args, "--sync-enabled"),
                intRequired(args, "--sync-interval"),
                intRequired(args, "--sync-overlap"),
                requiredOption(args, "--cursor-start-at"),
                intRequired(args, "--max-run-span-hours"),
                boolRequired(args, "--rescan-enabled"),
                intRequired(args, "--rescan-interval"),
                intRequired(args, "--rescan-max-age-days"),
                boolRequired(args, "--missing-batch-rescan-enabled"),
                intRequired(args, "--missing-batch-rescan-interval"),
                intRequired(args, "--missing-batch-rescan-max-age-days"));
    }

    private static void print(SettingsResponse resp) {
        printSettings(resp.settings());
        HihumbirdSyncState s = resp.state();
        System.out.printf("state lastSuccessAt=%s lastStartedAt=%s lastRunId=%s range=[%s,%s]%n",
                ExampleSupport.nvl(s.lastSuccessAt()), ExampleSupport.nvl(s.lastStartedAt()),
                ExampleSupport.nvl(s.lastRunId()), ExampleSupport.nvl(s.lastSuccessCreatedFrom()),
                ExampleSupport.nvl(s.lastSuccessCreatedTo()));
    }

    private static void printSettings(HihumbirdSettings s) {
        System.out.printf("settings syncEnabled=%s syncIntervalMinutes=%d syncOverlapMinutes=%d%n",
                s.syncEnabled(), s.syncIntervalMinutes(), s.syncOverlapMinutes());
        System.out.printf("         cursorStartAt=%s maxRunSpanHours=%d%n",
                s.cursorStartAt(), s.maxRunSpanHours());
        System.out.printf("         rescanPendingEnabled=%s rescanPendingIntervalMinutes=%d rescanPendingMaxAgeDays=%d%n",
                s.rescanPendingEnabled(), s.rescanPendingIntervalMinutes(), s.rescanPendingMaxAgeDays());
        System.out.printf("         rescanMissingBatchEnabled=%s rescanMissingBatchIntervalMinutes=%d rescanMissingBatchMaxAgeDays=%d%n",
                s.rescanMissingBatchEnabled(), s.rescanMissingBatchIntervalMinutes(),
                s.rescanMissingBatchMaxAgeDays());
    }

    private static String requiredOption(String[] args, String name) {
        String value = ExampleSupport.option(args, name, null);
        if (value == null) {
            System.err.println("missing option: " + name);
            System.exit(ExampleSupport.ERR_USAGE);
        }
        return value;
    }

    private static int intRequired(String[] args, String name) {
        return Integer.parseInt(requiredOption(args, name));
    }

    private static boolean boolRequired(String[] args, String name) {
        String value = requiredOption(args, name);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        System.err.println(name + " must be true or false");
        System.exit(ExampleSupport.ERR_USAGE);
        return false;
    }

    private static void usage() {
        System.err.println("usage:");
        System.err.println("  CrawlerSettingsExample get");
        System.err.println("  CrawlerSettingsExample update --sync-enabled true|false --sync-interval N --sync-overlap N");
        System.err.println("      --cursor-start-at ISO --max-run-span-hours N --rescan-enabled true|false");
        System.err.println("      --rescan-interval N --rescan-max-age-days N");
        System.err.println("      --missing-batch-rescan-enabled true|false --missing-batch-rescan-interval N");
        System.err.println("      --missing-batch-rescan-max-age-days N");
    }

    private CrawlerSettingsExample() {}
}
