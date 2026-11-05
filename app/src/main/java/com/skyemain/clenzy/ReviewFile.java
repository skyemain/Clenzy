package com.skyemain.clenzy;

public record ReviewFile(String uri, String name, long bytes, long modified, String digest,
                         boolean video, boolean download, boolean screenshot) {
    public ReviewFile(String uri, String name, long bytes, long modified, String digest) {
        this(uri, name, bytes, modified, digest, false, false, false);
    }
    public ReviewFile withDigest(String hash) {
        return new ReviewFile(uri, name, bytes, modified, hash, video, download, screenshot);
    }

    public boolean matches(long currentBytes, long currentModified) {
        return bytes >= 0 && modified > 0 && bytes == currentBytes && modified == currentModified;
    }
}
