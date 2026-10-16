package dev.lourencogabriel.wmssaas.mail;

import java.util.List;

/** Resources embedded by CID; rendering an email never fetches remote images. */
final class EmailBrandAssets {

    static final List<InlineImage> IMAGES = List.of(
            new InlineImage("wms-brand-icon", "mail/branding/wms-icon.png"),
            new InlineImage("wms-author-monogram", "mail/branding/author-monogram.png")
    );

    private EmailBrandAssets() {
    }

    record InlineImage(String contentId, String resourcePath) {
    }
}
