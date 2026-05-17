package com.basir.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Lightweight on-device .pptx parser. A .pptx file is just a ZIP archive
 * containing:
 *   - ppt/slides/slideN.xml       (text content per slide)
 *   - ppt/slides/_rels/slideN.xml.rels (links slide -> images / charts)
 *   - ppt/media/*.png|jpg|...     (the actual images embedded in the deck)
 *
 * For Basir we don't need a full OOXML parser. We only need:
 *   1) The text of each slide (so Gemini can summarise it).
 *   2) The image bytes embedded on each slide (so Gemini can describe them).
 *
 * This class extracts that data with zero external libraries.
 */
public final class PptxExtractor {

    public static final class SlideMedia {
        public final String name;       // e.g. "image1.png"
        public final String mimeType;
        public final byte[] bytes;
        SlideMedia(String name, String mimeType, byte[] bytes) {
            this.name = name; this.mimeType = mimeType; this.bytes = bytes;
        }
    }

    public static final class Slide {
        public final int index;             // 1-based slide number
        public final String text;
        public final List<SlideMedia> images;
        Slide(int index, String text, List<SlideMedia> images) {
            this.index = index;
            this.text = text;
            this.images = images;
        }
    }

    /** Parsed structure of a single .pptx file. */
    public static final class Deck {
        public final List<Slide> slides = new ArrayList<>();
    }

    private static final Pattern TEXT_PATTERN =
            Pattern.compile("<a:t[^>]*>(.*?)</a:t>", Pattern.DOTALL);

    private static final Pattern SLIDE_NAME_PATTERN =
            Pattern.compile("^ppt/slides/slide(\\d+)\\.xml$");

    private static final Pattern SLIDE_RELS_PATTERN =
            Pattern.compile("^ppt/slides/_rels/slide(\\d+)\\.xml\\.rels$");

    private static final Pattern REL_TARGET_PATTERN =
            Pattern.compile("Target=\"([^\"]+)\"");

    private PptxExtractor() {}

    /**
     * Read a .pptx file and return its slides in order.
     * Slides without text/images are skipped.
     */
    public static Deck parse(Context ctx, Uri uri) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        // Holders keyed by slide number.
        Map<Integer, String> slideXml = new LinkedHashMap<>();
        Map<Integer, String> slideRelsXml = new LinkedHashMap<>();
        Map<String, byte[]> media = new LinkedHashMap<>();

        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new Exception("Could not open the .pptx file");
            try (ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry e;
                byte[] buf = new byte[8192];
                while ((e = zip.getNextEntry()) != null) {
                    String name = e.getName();

                    Matcher mSlide = SLIDE_NAME_PATTERN.matcher(name);
                    Matcher mRels  = SLIDE_RELS_PATTERN.matcher(name);

                    if (mSlide.matches()) {
                        int idx = Integer.parseInt(mSlide.group(1));
                        slideXml.put(idx, readAll(zip, buf));
                    } else if (mRels.matches()) {
                        int idx = Integer.parseInt(mRels.group(1));
                        slideRelsXml.put(idx, readAll(zip, buf));
                    } else if (name.startsWith("ppt/media/")) {
                        String shortName = name.substring("ppt/media/".length());
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        int n;
                        while ((n = zip.read(buf)) != -1) out.write(buf, 0, n);
                        media.put(shortName, out.toByteArray());
                    }
                    zip.closeEntry();
                }
            }
        }

        List<Integer> ordered = new ArrayList<>(slideXml.keySet());
        Collections.sort(ordered);

        Deck deck = new Deck();
        for (Integer idx : ordered) {
            String xml = slideXml.get(idx);
            String relsXml = slideRelsXml.get(idx);

            // 1) Collect visible text from a:t nodes.
            StringBuilder text = new StringBuilder();
            Matcher tm = TEXT_PATTERN.matcher(xml);
            while (tm.find()) {
                String chunk = unescapeXml(tm.group(1)).trim();
                if (!chunk.isEmpty()) {
                    if (text.length() > 0) text.append('\n');
                    text.append(chunk);
                }
            }

            // 2) Collect media references for this slide.
            List<SlideMedia> images = new ArrayList<>();
            if (relsXml != null) {
                Matcher rm = REL_TARGET_PATTERN.matcher(relsXml);
                while (rm.find()) {
                    String target = rm.group(1);
                    int lastSlash = Math.max(target.lastIndexOf('/'), target.lastIndexOf('\\'));
                    String shortName = (lastSlash >= 0) ? target.substring(lastSlash + 1) : target;
                    byte[] data = media.get(shortName);
                    if (data == null) continue;
                    String mime = guessMime(shortName);
                    // Filter only image-like media (skip charts, audio, etc.)
                    if (mime.startsWith("image/")) {
                        images.add(new SlideMedia(shortName, mime, data));
                    }
                }
            }

            if (text.length() == 0 && images.isEmpty()) continue; // skip empty
            deck.slides.add(new Slide(idx, text.toString(), images));
        }
        return deck;
    }

    private static String readAll(ZipInputStream zip, byte[] buf) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        while ((n = zip.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), "UTF-8");
    }

    private static String unescapeXml(String s) {
        if (s == null) return "";
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String guessMime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif"))  return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".bmp"))  return "image/bmp";
        if (n.endsWith(".svg"))  return "image/svg+xml";
        return "application/octet-stream";
    }
}
