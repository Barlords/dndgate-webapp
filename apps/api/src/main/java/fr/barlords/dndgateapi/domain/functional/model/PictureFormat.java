package fr.barlords.dndgateapi.domain.functional.model;

import java.util.Optional;

/** The image formats accepted for a picture. They are recognised by the content of the file, never by what the client declares. */
public enum PictureFormat {

    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final String contentType;

    private final String extension;

    PictureFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** The format of a picture stored by this application, from the extension it was given (see GameCharacterCreatorService). */
    public static Optional<PictureFormat> fromPath(String path) {
        if (path == null) {
            return Optional.empty();
        }
        for (PictureFormat format : values()) {
            if (path.endsWith("." + format.extension)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    public static Optional<PictureFormat> detect(byte[] content) {
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return Optional.of(JPEG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
