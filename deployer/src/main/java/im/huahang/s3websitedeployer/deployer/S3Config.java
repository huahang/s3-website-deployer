package im.huahang.s3websitedeployer.deployer;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public class S3Config {
    public static String CONFIG_FILE_NAME = ".s3_website_deployer_config.json";

    public static class ConfigItem {
        @SerializedName("region")
        public String region = "";

        @SerializedName("bucket")
        public String bucketName = "";

        @SerializedName("distribution_id")
        public String distributionID = "";

        @SerializedName("app_key")
        public String appKey = "";

        @SerializedName("app_secret")
        public String appSecret = "";

        public CredentialsProvider getCredentialsProvider() {
            return new CredentialsProvider();
        }

        public class CredentialsProvider implements AWSCredentialsProvider {
            @Override
            public AWSCredentials getCredentials() {
                AWSCredentials awsCredentials = new AWSCredentials() {
                    @Override
                    public String getAWSAccessKeyId() {
                        return ConfigItem.this.appKey;
                    }

                    @Override
                    public String getAWSSecretKey() {
                        return ConfigItem.this.appSecret;
                    }
                };
                return awsCredentials;
            }

            @Override
            public void refresh() {

            }
        }
    }

    @SerializedName("config_items")
    public Map<String, ConfigItem> configItems = new TreeMap<>();

    public static S3Config load() {
        final File configFile = new File(getConfigFileName());
        if (!(configFile.exists() && configFile.isFile())) {
            return null;
        }
        Gson gson = new Gson();
        S3Config s3Config;
        try {
            s3Config = gson.fromJson(
                new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(configFile)
                    )
                ),
                S3Config.class
            );
        } catch (FileNotFoundException e) {
            return null;
        }
        return s3Config;
    }

    public void save() throws IOException {
        final File configFile = new File(getConfigFileName());
        configFile.delete();
        Writer writer = null;
        try {
            configFile.createNewFile();
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            writer = new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(configFile)
                )
            );
            String jsonString = gson.toJson(this);
            writer.write(jsonString);
            writer.flush();
        } catch (IOException e) {
            throw e;
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static String getConfigFileName() {
        final String userHome = System.getProperty("user.home");
        return "" + userHome + File.separator + CONFIG_FILE_NAME;
    }
}
