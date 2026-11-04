package io.github.hoanghaisol.blocktube.channelfilter.model;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Model and vocabulary are not in the APK: they are downloaded from a GitHub release the first
 * time the feature is enabled, verified by SHA-256, then moved into place (so a present file is
 * always a verified one).
 */
public class ModelFiles {
    private static final String BASE_URL = "https://github.com/hoanghaisol/BlockTube/releases/download/model-e5-small-int8-v1/";
    public static final String MODEL = "model_quantized.onnx";
    public static final String VOCAB = "vocab.bin";
    private static final String[][] FILES = {
            {MODEL, "f80102d3f2a1229f387d3c81909990d8945513e347b0eab049f7de3c6f98c193"},
            {VOCAB, "a1f2e2285dfb3dce33bb36ee56908f613cccddd1b7760757ead6823ffad1561a"},
    };

    public interface ProgressListener {
        void onProgress(String fileName, long downloaded);
    }

    private final File mDir;

    public ModelFiles(File dir) {
        mDir = dir;
    }

    public File get(String name) {
        return new File(mDir, name);
    }

    public boolean isReady() {
        for (String[] file : FILES) {
            if (!get(file[0]).isFile()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Blocking. Downloads the missing files only.
     */
    public void download(ProgressListener listener) throws IOException {
        if (!mDir.isDirectory() && !mDir.mkdirs()) {
            throw new IOException("Can't create " + mDir);
        }

        for (String[] file : FILES) {
            File target = get(file[0]);
            if (!target.isFile()) {
                downloadVerified(BASE_URL + file[0], file[1], target, listener);
            }
        }
    }

    public void delete() {
        for (String[] file : FILES) {
            get(file[0]).delete();
        }
    }

    private static void downloadVerified(String url, String sha256, File target, ProgressListener listener) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true); // GitHub redirects to its CDN (https → https)

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode() + " for " + url);
            }

            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    total += read;
                    if (listener != null) {
                        listener.onProgress(target.getName(), total);
                    }
                }
            }
        } catch (IOException e) {
            tmp.delete();
            throw e;
        } finally {
            connection.disconnect();
        }

        if (!sha256.equals(toHex(digest.digest()))) {
            tmp.delete();
            throw new IOException("Checksum mismatch for " + target.getName());
        }

        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("Can't move " + tmp);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
