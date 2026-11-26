package io.github.xiaotong6666.maihoku.features.googleone

/**
 * Display catalog: maps Play-returned sku + offerId into readable plan tiers,
 * introductory pricing, and regional availability hints.
 *
 * Only affects list display text; does not participate in the purchase flow
 * or spoof offers not returned by Google Play.
 * `regionLocked` indicates regional promotions (*_ip_*p intro prices, g1.play.* pre-installed tiers),
 * which are hidden for most accounts and require matching regions (e.g., US) to appear.
 */
internal object PlanCatalog {
    data class Entry(
        val title: String,
        val regionLocked: Boolean,
        val order: Int,
    )

    private val BY_SKU_OFFER: Map<String, Entry> = mapOf(
        // Basic Storage (30GB / 100GB / 200GB)
        key("g1.30gb.3month_eft", "freetrial", "Basic 30GB · 3 Months Free (then $0.89/mo)", false, 10),
        key("g1.30gb.1month_eft", "freetrial", "Basic 30GB · 1 Month Free (then $0.89/mo)", false, 11),
        key("g1.100gb.1year_eft", "freetrial", "Basic 100GB · 12 Months Free (then $1.99/mo)", false, 20),
        key("g1.100gb.annual.1year_eft", "freetrial", "Basic 100GB (Annual) · 12 Months Free (then $19.99/yr)", false, 21),
        key("g1.100gb.9months_eft", "freetrial", "Basic 100GB · 9 Months Free (then $1.99/mo)", false, 22),
        key("g1.100gb.180days_eft", "freetrial", "Basic 100GB · 180 Days Free (then $1.99/mo)", false, 23),
        key("g1.200gb.1year_eft", "freetrial", "Standard 200GB · 12 Months Free (then $2.99/mo)", false, 30),
        key("g1.200gb.180days_eft", "freetrial", "Standard 200GB · 180 Days Free (then $2.99/mo)", false, 31),

        // AI Plus 400GB (helium)
        key("g1.play.helium", "eft-12mo", "AI Plus 400GB · 12 Months Free (then $4.99/mo)", true, 110),
        key("g1.play.helium", "eft-6mo", "AI Plus 400GB · 6 Months Free (then $4.99/mo)", true, 111),
        key("g1.play.helium", "eft-3mo", "AI Plus 400GB · 3 Months Free (then $4.99/mo)", true, 112),
        key("g1.play.helium", "eft-1mo", "AI Plus 400GB · 1 Month Free (then $4.99/mo)", true, 113),
        key("g1.play.helium.student", "eft-12mo", "AI Plus 400GB (Student) · 12 Months Free (then $4.99/mo)", true, 114),
        key("g1.helium.12months_ip_90p", "introprice", "AI Plus 400GB · $0.49/mo for 12 Months (then $4.99/mo)", true, 115),
        key("g1.helium.3months_ip_75p.acq", "introprice", "AI Plus 400GB · $1.29/mo for 3 Months (then $4.99/mo)", true, 116),

        // AI Plus 2TB
        key("g1.2tb.1year_eft", "freetrial", "AI Plus 2TB · 12 Months Free (then $9.99/mo)", false, 120),
        key("g1.2tb.subs_variant", "6months-eft", "AI Plus 2TB · 6 Months Free (then $9.99/mo)", false, 121),
        key("g1.2tb.subs_variant", "3months-eft", "AI Plus 2TB · 3 Months Free (then $9.99/mo)", false, 122),
        key("g1.2tb.6months_eft", "freetrial", "AI Plus 2TB · 6 Months Free (then $9.99/mo)", false, 123),
        key("g1.2tb.3months_ip_90p.acq", "introprice", "AI Plus 2TB · $0.99/mo for 3 Months (then $9.99/mo)", true, 124),
        key("g1.2tb.3months_ip_85p.acq", "introprice", "AI Plus 2TB · $1.49/mo for 3 Months (then $9.99/mo)", true, 125),

        // AI Pro 5TB / 10TB
        key("g1.2tb.ai.18months_eft", "eft-18months", "AI Pro 5TB · 18 Months Free (then $19.99/mo)", false, 200),
        key("g1.2tb.ai.15months_eft", "eft-15months", "AI Pro 5TB · 15 Months Free (then $19.99/mo)", false, 201),
        key("g1.2tb.ai.12months_eft", "eft-12months", "AI Pro 5TB · 12 Months Free (then $19.99/mo)", false, 202),
        key("g1.2tb.ai.subs_variant", "12months-eft", "AI Pro 5TB · 12 Months Free (then $19.99/mo)", false, 203),
        key("g1.play.2tb.ai.student", "eft-12mo", "AI Pro 5TB (Student) · 12 Months Free (then $19.99/mo)", true, 204),
        key("g1.2tb.ai.6months_eft", "eft-6months", "AI Pro 5TB · 6 Months Free (then $19.99/mo)", false, 205),
        key("g1.2tb.ai.subs_variant", "6months-eft", "AI Pro 5TB · 6 Months Free (then $19.99/mo)", false, 206),
        key("g1.2tb.ai.subs_variant", "6months-eft-2", "AI Pro 5TB · 6 Months Free (Alt 2)", false, 207),
        key("g1.2tb.ai.subs_variant", "6months-eft-ss-2026", "AI Pro 5TB · 6 Months Free (SS 2026)", false, 208),
        key("g1.2tb.ai.4months_eft", "eft-4months", "AI Pro 5TB · 4 Months Free (then $19.99/mo)", false, 209),
        key("g1.2tb.ai.3months_eft", "eft-3months", "AI Pro 5TB · 3 Months Free (then $19.99/mo)", false, 210),
        key("g1.play.2tb.ai", "eft-3mo", "AI Pro 5TB · 3 Months Free (then $19.99/mo)", true, 211),
        key("g1.2tb.ai.2months_eft", "eft-2months", "AI Pro 5TB · 2 Months Free (then $19.99/mo)", false, 212),
        key("g1.2tb.ai.12months_ip_90p", "ip-12mo-90p", "AI Pro 5TB · $1.99/mo for 12 Months (then $19.99/mo)", true, 213),
        key("g1.2tb.ai.12months_ip_75p", "introprice", "AI Pro 5TB · $4.99/mo for 12 Months (then $19.99/mo)", true, 214),
        key("g1.2tb.ai.3months_ip_90p.acq", "introprice", "AI Pro 5TB · $1.99/mo for 3 Months (then $19.99/mo)", true, 215),
        key("g1.2tb.ai.3months_ip_75p.acq", "introprice", "AI Pro 5TB · $4.99/mo for 3 Months (then $19.99/mo)", true, 216),
        key("g1.2tb.ai.2months_ip_90p.acq", "introprice", "AI Pro 5TB · $1.99/mo for 2 Months (then $19.99/mo)", true, 217),
        key("g1.2tb.ai.1month_ip_95p.acq", "introprice", "AI Pro 5TB · $0.99 for 1st Month (then $19.99/mo)", true, 218),
        key("g1.10tb.1month_ip_90p", "introprice", "AI Pro 10TB · $4.99 for 1st Month (then $49.99/mo)", true, 219),
        key("g1.10tb.3months_ip_85p", "introprice", "AI Pro 10TB · $7.49/mo for 3 Months (then $49.99/mo)", true, 220),

        // AI Ultra 5x (20TB)
        key("g1.20tb.1month_eft.acq", "freetrial", "AI Ultra 5x 20TB · 1 Month Free (then $99.99/mo)", true, 305),
        key("g1.20tb.3months_ip_90p.acq", "introprice", "AI Ultra 5x 20TB · $9.99/mo for 3 Months (then $99.99/mo)", true, 310),
        key("g1.20tb.3months_ip_80p.acq", "introprice", "AI Ultra 5x 20TB · $19.99/mo for 3 Months (then $99.99/mo)", true, 311),
        key("g1.20tb.3months_ip_80p", "introprice", "AI Ultra 5x 20TB · $19.99/mo for 3 Months (then $99.99/mo)", true, 312),
        key("g1.20tb.3months_ip_75p.acq", "introprice", "AI Ultra 5x 20TB · $24.99/mo for 3 Months (then $99.99/mo)", true, 313),

        // AI Ultra 20x (30TB)
        key("g1.ai.ultra.1month_eft.acq", "freetrial", "AI Ultra 20x 30TB · 1 Month Free (then $199.99/mo)", true, 405),
        key("g1.ai.ultra.1month_ip_90p.acq", "introprice", "AI Ultra 20x 30TB · $19.99 for 1st Month (then $199.99/mo)", true, 410),
        key("g1.ai.ultra.3months_ip_90p.acq", "introprice", "AI Ultra 20x 30TB · $19.99/mo for 3 Months (then $199.99/mo)", true, 411),
        key("g1.ai.ultra.3months_ip_80p.acq", "introprice", "AI Ultra 20x 30TB · $39.99/mo for 3 Months (then $199.99/mo)", true, 412),
        key("g1.ai.ultra.1month_ip_75p.acq", "introprice", "AI Ultra 20x 30TB · $49.99 for 1st Month (then $199.99/mo)", true, 413),
        key("g1.ai.ultra.3months_ip_75p.acq", "introprice", "AI Ultra 20x 30TB · $49.99/mo for 3 Months (then $199.99/mo)", true, 414),

        // YouTube Add-ons
        key("g1.youtube_premium_individual_add_on", "eft-1mo", "YouTube Premium Individual · 1 Month Free (Add-on)", true, 510),
        key("g1.youtube_premium_family_add_on", "eft-1mo", "YouTube Premium Family · 1 Month Free (Add-on)", true, 511),
        key("g1.youtube_premium_lite_add_on", "eft-1mo", "YouTube Premium Lite · 1 Month Free (Add-on)", true, 512),
    )

    /** Fallback descriptions when SKU is known but exact offerId is unmatched or base plan. */
    private val BY_SKU: Map<String, Entry> = mapOf(
        // Basic Storage Fallbacks
        "g1.30gb.3month_eft" to Entry("Basic 30GB · 3 Months Free (then $0.89/mo)", false, 15),
        "g1.30gb.1month_eft" to Entry("Basic 30GB · 1 Month Free (then $0.89/mo)", false, 16),
        "g1.100gb.1year_eft" to Entry("Basic 100GB · 12 Months Free (then $1.99/mo)", false, 25),
        "g1.100gb.annual.1year_eft" to Entry("Basic 100GB (Annual) · 12 Months Free (then $19.99/yr)", false, 26),
        "g1.100gb.9months_eft" to Entry("Basic 100GB · 9 Months Free (then $1.99/mo)", false, 27),
        "g1.100gb.180days_eft" to Entry("Basic 100GB · 180 Days Free (then $1.99/mo)", false, 28),
        "g1.200gb.1year_eft" to Entry("Standard 200GB · 12 Months Free (then $2.99/mo)", false, 35),
        "g1.200gb.180days_eft" to Entry("Standard 200GB · 180 Days Free (then $2.99/mo)", false, 36),

        // AI Plus Fallbacks
        "g1.play.helium" to Entry("AI Plus 400GB · Play Tier", true, 118),
        "g1.play.helium.student" to Entry("AI Plus 400GB (Student) · 12 Months Free (then $4.99/mo)", true, 119),
        "g1.helium.12months_ip_90p" to Entry("AI Plus 400GB · $0.49/mo for 12 Months (then $4.99/mo)", true, 119),
        "g1.2tb.1year_eft" to Entry("AI Plus 2TB · 12 Months Free (then $9.99/mo)", false, 128),
        "g1.2tb.3months_ip_90p.acq" to Entry("AI Plus 2TB · $0.99/mo for 3 Months (then $9.99/mo)", true, 129),

        // AI Pro Fallbacks
        "g1.2tb.ai.18months_eft" to Entry("AI Pro 5TB · 18 Months Free (then $19.99/mo)", false, 230),
        "g1.2tb.ai.15months_eft" to Entry("AI Pro 5TB · 15 Months Free (then $19.99/mo)", false, 231),
        "g1.2tb.ai.12months_eft" to Entry("AI Pro 5TB · 12 Months Free (then $19.99/mo)", false, 232),
        "g1.play.2tb.ai.student" to Entry("AI Pro 5TB (Student) · 12 Months Free (then $19.99/mo)", true, 233),
        "g1.2tb.ai.6months_eft" to Entry("AI Pro 5TB · 6 Months Free (then $19.99/mo)", false, 234),
        "g1.2tb.ai.4months_eft" to Entry("AI Pro 5TB · 4 Months Free (then $19.99/mo)", false, 235),
        "g1.2tb.ai.3months_eft" to Entry("AI Pro 5TB · 3 Months Free (then $19.99/mo)", false, 236),
        "g1.2tb.ai.2months_eft" to Entry("AI Pro 5TB · 2 Months Free (then $19.99/mo)", false, 237),
        "g1.2tb.ai.12months_ip_90p" to Entry("AI Pro 5TB · $1.99/mo for 12 Months (then $19.99/mo)", true, 238),
        "g1.2tb.ai.12months_ip_75p" to Entry("AI Pro 5TB · $4.99/mo for 12 Months (then $19.99/mo)", true, 239),
        "g1.2tb.ai.3months_ip_90p.acq" to Entry("AI Pro 5TB · $1.99/mo for 3 Months (then $19.99/mo)", true, 240),
        "g1.2tb.ai.2months_ip_90p.acq" to Entry("AI Pro 5TB · $1.99/mo for 2 Months (then $19.99/mo)", true, 241),
        "g1.2tb.ai.1month_ip_95p.acq" to Entry("AI Pro 5TB · $0.99 for 1st Month (then $19.99/mo)", true, 242),
        "g1.play.ai.pro" to Entry("Pro · Play Base SKU (No Promo)", true, 290),

        // AI Ultra Fallbacks
        "g1.20tb.1month_eft.acq" to Entry("AI Ultra 5x 20TB · 1 Month Free (then $99.99/mo)", true, 320),
        "g1.20tb.3months_ip_80p" to Entry("AI Ultra 5x 20TB · $19.99/mo for 3 Months (then $99.99/mo)", true, 321),
        "g1.play.20tb" to Entry("Ultra 5x 20TB · Play Base SKU (No Promo)", true, 390),
        "g1.ai.ultra.1month_eft.acq" to Entry("AI Ultra 20x 30TB · 1 Month Free (then $199.99/mo)", true, 420),
        "g1.30tb" to Entry("Ultra 20x 30TB · Base SKU (No Promo)", false, 490),
    )

    private fun key(sku: String, offerId: String, title: String, regionLocked: Boolean, order: Int) =
        "$sku|$offerId" to Entry(title, regionLocked, order)

    fun find(sku: String, offerId: String?): Entry? =
        BY_SKU_OFFER["$sku|${offerId.orEmpty()}"] ?: BY_SKU[sku]

    fun orderOf(sku: String, offerId: String?): Int = find(sku, offerId)?.order ?: 999
}
