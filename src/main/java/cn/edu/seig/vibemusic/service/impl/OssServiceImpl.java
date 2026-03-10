package cn.edu.seig.vibemusic.service.impl;

import cn.edu.seig.vibemusic.constant.MessageConstant;
import cn.edu.seig.vibemusic.service.MinioService;
import com.aliyun.oss.OSS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@Primary
@Service
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
public class OssServiceImpl implements MinioService {

    private final OSS ossClient;

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.bucket}")
    private String bucketName;

    @Value("${oss.publicDomain:}")
    private String publicDomain;

    public OssServiceImpl(OSS ossClient) {
        this.ossClient = ossClient;
    }

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        try {
            String objectName = folder + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
            InputStream inputStream = file.getInputStream();
            ossClient.putObject(bucketName, objectName, inputStream);
            return buildFileUrl(objectName);
        } catch (Exception e) {
            throw new RuntimeException(MessageConstant.FILE_UPLOAD + MessageConstant.FAILED + "：" + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            String objectName = extractObjectName(fileUrl);
            ossClient.deleteObject(bucketName, objectName);
        } catch (Exception e) {
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    private String buildFileUrl(String objectName) {
        if (StringUtils.hasText(publicDomain)) {
            return trimTrailingSlash(publicDomain) + "/" + objectName;
        }
        String normalizedEndpoint = endpoint.replaceFirst("^https?://", "");
        return "https://" + bucketName + "." + normalizedEndpoint + "/" + objectName;
    }

    private String extractObjectName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("fileUrl is empty");
        }
        if (StringUtils.hasText(publicDomain)) {
            String prefix = trimTrailingSlash(publicDomain) + "/";
            if (fileUrl.startsWith(prefix)) {
                return fileUrl.substring(prefix.length());
            }
        }

        URI uri = URI.create(fileUrl);
        String path = uri.getPath();
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("object path not found in fileUrl");
        }
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String bucketPathPrefix = bucketName + "/";
        if (normalizedPath.startsWith(bucketPathPrefix)) {
            return normalizedPath.substring(bucketPathPrefix.length());
        }
        return normalizedPath;
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
