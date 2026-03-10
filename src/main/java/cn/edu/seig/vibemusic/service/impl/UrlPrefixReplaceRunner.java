package cn.edu.seig.vibemusic.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "migration.url-prefix-replace", name = "enabled", havingValue = "true")
public class UrlPrefixReplaceRunner implements CommandLineRunner {

    private static final List<TableColumn> TARGET_COLUMNS = List.of(
            new TableColumn("tb_song", "cover_url"),
            new TableColumn("tb_song", "audio_url"),
            new TableColumn("tb_artist", "avatar"),
            new TableColumn("tb_playlist", "cover_url"),
            new TableColumn("tb_banner", "banner_url"),
            new TableColumn("tb_user", "user_avatar")
    );

    private final JdbcTemplate jdbcTemplate;

    @Value("${migration.url-prefix-replace.dry-run:true}")
    private boolean dryRun;

    @Value("${migration.url-prefix-replace.fail-fast:false}")
    private boolean failFast;

    @Value("${migration.url-prefix-replace.old-prefix:}")
    private String oldPrefixConfig;

    @Value("${migration.url-prefix-replace.new-prefix:}")
    private String newPrefixConfig;

    @Value("${minio.endpoint:}")
    private String minioEndpoint;

    @Value("${minio.bucket:}")
    private String minioBucket;

    @Value("${oss.endpoint:}")
    private String ossEndpoint;

    @Value("${oss.bucket:}")
    private String ossBucket;

    @Value("${oss.publicDomain:}")
    private String ossPublicDomain;

    public UrlPrefixReplaceRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String oldPrefix = normalizePrefix(resolveOldPrefix());
        String newPrefix = normalizePrefix(resolveNewPrefix());

        if (!StringUtils.hasText(oldPrefix) || !StringUtils.hasText(newPrefix)) {
            throw new IllegalArgumentException("URL prefix replace requires non-empty oldPrefix and newPrefix.");
        }
        if (oldPrefix.equals(newPrefix)) {
            throw new IllegalArgumentException("oldPrefix and newPrefix are the same. Aborting replacement.");
        }

        log.info("Starting URL prefix replace. dryRun={}, failFast={}, oldPrefix={}, newPrefix={}",
                dryRun, failFast, oldPrefix, newPrefix);

        long totalMatched = 0L;
        long totalUpdated = 0L;

        for (TableColumn tableColumn : TARGET_COLUMNS) {
            try {
                long matched = countMatches(tableColumn, oldPrefix);
                totalMatched += matched;

                if (matched == 0) {
                    continue;
                }

                if (dryRun) {
                    log.info("[DRY-RUN] {}.{} matched rows={}", tableColumn.table(), tableColumn.column(), matched);
                    continue;
                }

                int updated = replacePrefix(tableColumn, oldPrefix, newPrefix);
                totalUpdated += updated;
                log.info("Updated {}.{} rows={}", tableColumn.table(), tableColumn.column(), updated);
            } catch (Exception e) {
                log.error("Prefix replace failed at {}.{}. Reason={}",
                        tableColumn.table(), tableColumn.column(), e.getMessage(), e);
                if (failFast) {
                    throw new RuntimeException("URL prefix replace aborted due to fail-fast", e);
                }
            }
        }

        log.info("URL prefix replace finished. matched={}, updated={}, dryRun={}",
                totalMatched, totalUpdated, dryRun);
    }

    private long countMatches(TableColumn tableColumn, String oldPrefix) {
        String sql = "SELECT COUNT(1) FROM " + tableColumn.table() + " WHERE " + tableColumn.column() + " LIKE ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, oldPrefix + "%");
        return count == null ? 0L : count;
    }

    private int replacePrefix(TableColumn tableColumn, String oldPrefix, String newPrefix) {
        String sql = "UPDATE " + tableColumn.table() +
                " SET " + tableColumn.column() + " = REPLACE(" + tableColumn.column() + ", ?, ?) " +
                " WHERE " + tableColumn.column() + " LIKE ?";
        return jdbcTemplate.update(sql, oldPrefix, newPrefix, oldPrefix + "%");
    }

    private String resolveOldPrefix() {
        if (StringUtils.hasText(oldPrefixConfig)) {
            return oldPrefixConfig;
        }
        if (!StringUtils.hasText(minioEndpoint) || !StringUtils.hasText(minioBucket)) {
            return "";
        }
        return trimTrailingSlash(minioEndpoint) + "/" + minioBucket + "/";
    }

    private String resolveNewPrefix() {
        if (StringUtils.hasText(newPrefixConfig)) {
            return newPrefixConfig;
        }
        if (StringUtils.hasText(ossPublicDomain)) {
            return trimTrailingSlash(ossPublicDomain) + "/";
        }
        if (!StringUtils.hasText(ossEndpoint) || !StringUtils.hasText(ossBucket)) {
            return "";
        }
        String endpoint = ossEndpoint.replaceFirst("^https?://", "");
        return "https://" + ossBucket + "." + endpoint + "/";
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return prefix;
        }
        return trimTrailingSlash(prefix) + "/";
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record TableColumn(String table, String column) {
    }
}
