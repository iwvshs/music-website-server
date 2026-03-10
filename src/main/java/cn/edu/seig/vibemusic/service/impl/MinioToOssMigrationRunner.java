package cn.edu.seig.vibemusic.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.GetObjectResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnBean(OSS.class)
@ConditionalOnProperty(prefix = "migration.minio-to-oss", name = "enabled", havingValue = "true")
public class MinioToOssMigrationRunner implements CommandLineRunner {

    private static final List<TableColumn> TARGET_COLUMNS = List.of(
            new TableColumn("tb_song", "cover_url"),
            new TableColumn("tb_song", "audio_url"),
            new TableColumn("tb_artist", "avatar"),
            new TableColumn("tb_playlist", "cover_url"),
            new TableColumn("tb_banner", "banner_url"),
            new TableColumn("tb_user", "user_avatar")
    );

    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final OSS ossClient;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.bucket}")
    private String minioBucket;

    @Value("${oss.bucket}")
    private String ossBucket;

    @Value("${migration.minio-to-oss.dry-run:true}")
    private boolean dryRun;

    @Value("${migration.minio-to-oss.fail-fast:false}")
    private boolean failFast;

    public MinioToOssMigrationRunner(JdbcTemplate jdbcTemplate, MinioClient minioClient, OSS ossClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.ossClient = ossClient;
    }

    @Override
    public void run(String... args) {
        MigrationStats stats = new MigrationStats();
        log.info("Starting MinIO -> OSS migration. dryRun={}, failFast={}, minioBucket={}, ossBucket={}",
                dryRun, failFast, minioBucket, ossBucket);

        for (TableColumn tableColumn : TARGET_COLUMNS) {
            List<String> urlList = fetchUrls(tableColumn);
            for (String fileUrl : urlList) {
                stats.total++;
                try {
                    migrateOne(fileUrl, stats);
                } catch (Exception e) {
                    stats.failed++;
                    log.error("Migration failed for url={}. Reason={}", fileUrl, e.getMessage(), e);
                    if (failFast) {
                        throw new RuntimeException("MinIO -> OSS migration aborted due to fail-fast", e);
                    }
                }
            }
        }

        log.info("MinIO -> OSS migration finished. total={}, migrated={}, existed={}, skipped={}, failed={}, dryRun={}",
                stats.total, stats.migrated, stats.existed, stats.skipped, stats.failed, dryRun);
    }

    private List<String> fetchUrls(TableColumn tableColumn) {
        String sql = "SELECT DISTINCT " + tableColumn.column + " FROM " + tableColumn.table +
                " WHERE " + tableColumn.column + " IS NOT NULL AND " + tableColumn.column + " <> ''";
        List<String> raw = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1));
        List<String> urlList = new ArrayList<>();
        for (String url : raw) {
            if (StringUtils.hasText(url)) {
                urlList.add(url.trim());
            }
        }
        log.info("Loaded {} URLs from {}.{}", urlList.size(), tableColumn.table, tableColumn.column);
        return urlList;
    }

    private void migrateOne(String fileUrl, MigrationStats stats) throws Exception {
        String objectName = extractObjectName(fileUrl);
        if (!StringUtils.hasText(objectName)) {
            stats.skipped++;
            return;
        }

        if (dryRun) {
            log.info("[DRY-RUN] would migrate object: {}", objectName);
            stats.skipped++;
            return;
        }

        if (ossClient.doesObjectExist(ossBucket, objectName)) {
            stats.existed++;
            return;
        }

        StatObjectResponse stat = minioClient.statObject(
                StatObjectArgs.builder().bucket(minioBucket).object(objectName).build()
        );

        try (GetObjectResponse inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(minioBucket).object(objectName).build()
        )) {
            ObjectMetadata metadata = new ObjectMetadata();
            if (StringUtils.hasText(stat.contentType())) {
                metadata.setContentType(stat.contentType());
            }
            ossClient.putObject(ossBucket, objectName, inputStream, metadata);
            stats.migrated++;
        }
    }

    private String extractObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }

        String prefix = normalizeMinioPrefix();
        if (!fileUrl.startsWith(prefix)) {
            log.warn("Skip non-minio url: {}", fileUrl);
            return null;
        }

        String objectName = fileUrl.substring(prefix.length());
        if (!StringUtils.hasText(objectName)) {
            log.warn("Skip url with empty object key: {}", fileUrl);
            return null;
        }
        return objectName;
    }

    private String normalizeMinioPrefix() {
        String endpoint = minioEndpoint;
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioBucket + "/";
    }

    private static class MigrationStats {
        private long total;
        private long migrated;
        private long existed;
        private long skipped;
        private long failed;
    }

    private record TableColumn(String table, String column) {
    }
}
