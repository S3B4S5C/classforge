package com.classforge.assistant.vision;

final class JpegExifOrientation {

    private JpegExifOrientation() {
    }

    static int read(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) {
            return 1;
        }

        int offset = 2;
        while (offset + 4 <= jpeg.length) {
            if ((jpeg[offset] & 0xff) != 0xff) {
                break;
            }

            int marker = jpeg[offset + 1] & 0xff;
            offset += 2;

            if (marker == 0xd8 || marker == 0x01) {
                continue;
            }
            if (marker == 0xd9 || marker == 0xda) {
                break;
            }
            if (offset + 2 > jpeg.length) {
                break;
            }

            int segmentLength = u16be(jpeg, offset);
            if (segmentLength < 2 || offset + segmentLength > jpeg.length) {
                break;
            }

            if (marker == 0xe1 && segmentLength >= 10) {
                int data = offset + 2;
                if (matches(jpeg, data, "Exif\u0000\u0000")) {
                    int orientation = readTiff(jpeg, data + 6, offset + segmentLength);
                    if (orientation >= 1 && orientation <= 8) {
                        return orientation;
                    }
                }
            }

            offset += segmentLength;
        }

        return 1;
    }

    private static int readTiff(byte[] data, int tiff, int limit) {
        if (tiff + 8 > limit) {
            return 1;
        }

        boolean little;
        if (data[tiff] == 'I' && data[tiff + 1] == 'I') {
            little = true;
        } else if (data[tiff] == 'M' && data[tiff + 1] == 'M') {
            little = false;
        } else {
            return 1;
        }

        if (u16(data, tiff + 2, little) != 42) {
            return 1;
        }

        long ifdOffset = u32(data, tiff + 4, little);
        long ifd = (long) tiff + ifdOffset;
        if (ifd < 0 || ifd + 2 > limit) {
            return 1;
        }

        int entries = u16(data, (int) ifd, little);
        int entry = (int) ifd + 2;
        for (int index = 0; index < entries; index++) {
            if (entry + 12 > limit) {
                break;
            }
            int tag = u16(data, entry, little);
            if (tag == 0x0112) {
                int type = u16(data, entry + 2, little);
                long count = u32(data, entry + 4, little);
                if (type == 3 && count >= 1) {
                    return u16(data, entry + 8, little);
                }
                return 1;
            }
            entry += 12;
        }

        return 1;
    }

    private static boolean matches(byte[] bytes, int offset, String expected) {
        if (offset + expected.length() > bytes.length) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if ((byte) expected.charAt(index) != bytes[offset + index]) {
                return false;
            }
        }
        return true;
    }

    private static int u16be(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static int u16(byte[] bytes, int offset, boolean little) {
        if (little) {
            return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        }
        return u16be(bytes, offset);
    }

    private static long u32(byte[] bytes, int offset, boolean little) {
        if (little) {
            return (bytes[offset] & 0xffL)
                    | ((bytes[offset + 1] & 0xffL) << 8)
                    | ((bytes[offset + 2] & 0xffL) << 16)
                    | ((bytes[offset + 3] & 0xffL) << 24);
        }
        return ((bytes[offset] & 0xffL) << 24)
                | ((bytes[offset + 1] & 0xffL) << 16)
                | ((bytes[offset + 2] & 0xffL) << 8)
                | (bytes[offset + 3] & 0xffL);
    }
}
