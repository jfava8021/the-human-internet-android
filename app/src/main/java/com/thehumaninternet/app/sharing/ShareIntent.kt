package com.thehumaninternet.app.sharing

import com.thehumaninternet.app.models.VerifiedPhoto

/**
 * The platforms that get a **link intent**: their own composer, pre-filled
 * with the verification link. No image changes hands, and that's the point —
 * these platforms re-encode on upload, which strips the C2PA manifest, so an
 * uploaded picture arrives making a claim it can no longer back. The link
 * instead unfurls into the website's Open Graph card, which carries the photo
 * and stays tappable through to the real proof.
 *
 * The URL shapes are the iOS app's, pinned by `ShareIntentTest`.
 */
enum class ShareIntent {
    REDDIT,
    X;

    /**
     * The platform's own app scheme, tried before [composerURL]. X still
     * registers the legacy `twitter://` scheme. Reddit has no documented
     * compose deep link and relies on its app handling `reddit.com/submit`.
     */
    fun appURL(photo: VerifiedPhoto): String? = when (this) {
        REDDIT -> null
        X -> "twitter://post?" + encodedQuery("message" to caption(photo))
    }

    /**
     * The web composer. X gets the caption only — never a separate `url`
     * parameter, since X appends that to the body and the caption already
     * embeds the link, which would post it twice. Reddit gets `url` and
     * nothing else: its own title field has a good placeholder.
     */
    fun composerURL(photo: VerifiedPhoto): String = when (this) {
        REDDIT -> "https://www.reddit.com/submit?" + encodedQuery("url" to photo.verificationURL)
        X -> "https://x.com/intent/post?" + encodedQuery("text" to caption(photo))
    }

    companion object {
        /** The seeded caption. X's alone. */
        fun caption(photo: VerifiedPhoto) =
            "(Add your caption here.) Check this photo is real at ${photo.verificationURL}"

        private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

        /**
         * Strict percent-encoding: everything outside RFC 3986's unreserved
         * set, so the `:` and `/` of the nested link can't be misread as part
         * of the outer URL.
         */
        private fun encodedQuery(vararg items: Pair<String, String>) =
            items.joinToString("&") { (name, value) -> "${percentEncode(name)}=${percentEncode(value)}" }

        private fun percentEncode(text: String) = buildString {
            for (byte in text.toByteArray(Charsets.UTF_8)) {
                val character = byte.toInt().toChar()
                if (byte >= 0 && character in UNRESERVED) append(character)
                else append("%%%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}
