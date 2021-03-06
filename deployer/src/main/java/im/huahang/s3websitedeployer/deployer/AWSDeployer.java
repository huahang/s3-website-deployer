package im.huahang.s3websitedeployer.deployer;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.model.CreateInvalidationRequest;
import com.amazonaws.services.cloudfront.model.CreateInvalidationResult;
import com.amazonaws.services.cloudfront.model.InvalidationBatch;
import com.amazonaws.services.cloudfront.model.Paths;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.google.common.net.PercentEscaper;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

public class AWSDeployer {
    public AWSDeployer(
        final AmazonS3 s3,
        final String bucketName,
        final AmazonCloudFront cloudFront,
        final String distributionID,
        final Set<String> blacklist
    ) {
        this.s3 = s3;
        this.cloudFront = cloudFront;
        this.bucketName = bucketName;
        this.distributionID = distributionID;
        this.blacklist.addAll(blacklist);
    }

    public void uploadDirectory(
        final File s3Root,
        final File directory
    ) {
        String rootPath = s3Root.getAbsolutePath();
        String directoryPath = directory.getAbsolutePath();
        String destination = directoryPath.replaceFirst(rootPath, "");
        if (destination.startsWith("/")) {
            destination = destination.substring(1);
        }
        uploadDirectory(directory, destination);
    }

    public void uploadDirectory(
        final File directory,
        final String destination
    ) {
        String prefix = destination.endsWith("/") ? destination : destination + "/";
        prefix = StringUtils.isBlank(destination) ? "" : prefix;
        File[] files = directory.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                uploadDirectory(f, prefix + f.getName());
                continue;
            }
            if (f.isFile() && !blacklist.contains(f.getName())) {
                uploadFile(f, prefix + f.getName());
            }
        }
    }

    public void uploadFile(
        final File file,
        final String objectName
    ) {
        final String extension = StringUtils.trimToEmpty(getExtensionFromFilename(file.getName()));
        final String mime = ExtentionMimeTypeMap.getMimeType(extension.toLowerCase());
        PutObjectRequest request = new PutObjectRequest(bucketName, objectName, file);
        request.withCannedAcl(CannedAccessControlList.PublicRead);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(mime);
        metadata.setCacheControl("60");
        request.setMetadata(metadata);
        s3.putObject(request);
        uploadedObjects.add(objectName);
        System.out.println("Upload file success: " + objectName);
    }

    public void flushCDN() {
        if (StringUtils.isBlank(distributionID)) {
            System.out.println("No distribution_id configured, will not flush CDN.");
            return;
        }
        Set<String> invalidationPaths = new TreeSet<>();
        for (final String object : uploadedObjects) {
            PercentEscaper escaper = new PercentEscaper("/.-", true);
            String path = escaper.escape(object);
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            System.out.println("Invalidation path: " + path);
            invalidationPaths.add(path);
            if (!path.endsWith("/index.html")) {
                continue;
            }
            path = path.substring(0, path.length() - 10);
            if (StringUtils.isBlank(path)) {
                continue;
            }
            System.out.println("Invalidation path: " + path);
            invalidationPaths.add(path);
            path = path.substring(0, path.length() - 1);
            if (StringUtils.isBlank(path)) {
                continue;
            }
            System.out.println("Invalidation path: " + path);
            invalidationPaths.add(path);
        }
        Paths paths = new Paths();
        paths.setItems(invalidationPaths);
        paths.setQuantity(invalidationPaths.size());
        InvalidationBatch invalidationBatch = new InvalidationBatch();
        invalidationBatch.setCallerReference("" + System.currentTimeMillis());
        invalidationBatch.setPaths(paths);
        CreateInvalidationRequest request = new CreateInvalidationRequest();
        request.setDistributionId(distributionID);
        request.setInvalidationBatch(invalidationBatch);
        CreateInvalidationResult result = cloudFront.createInvalidation(request);
        System.out.println("Invalidation location: " + result.getLocation());
    }

    static private String getExtensionFromFilename(final String filename) {
        String[] splits = filename.split("\\.");
        if (splits.length < 2) {
            return "";
        }
        return splits[splits.length - 1];
    }

    private AmazonS3 s3;
    private String bucketName;

    private AmazonCloudFront cloudFront;
    private String distributionID;

    private Set<String> uploadedObjects = new TreeSet<>();

    private Set<String> blacklist = new TreeSet<>();
}
